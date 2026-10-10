<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { PageResult } from '@/api/types'
import { fetchAssetMeta } from '@/api/asset'
import type { AssetMeta } from '@/api/asset'
import { fetchCategoryTree } from '@/api/masterdata'
import type { CategoryNode } from '@/api/masterdata'
import { fetchRacks } from '@/api/room'
import {
  cancelInventoryTask,
  completeInventoryTask,
  createInventoryTask,
  fetchInventoryItems,
  fetchInventoryTaskDetail,
  fetchInventoryTasks,
  fetchMaterialMeta,
  fetchMaterialOptions,
  fetchWarehouseOptions,
  updateInventoryItem,
} from '@/api/warehouse'
import type {
  InventoryItemDetail,
  InventoryTargetType,
  InventoryTaskItem,
  MaterialMeta,
  MaterialOption,
  WarehouseOption,
} from '@/api/warehouse'

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

/* ---------------- 元数据 ---------------- */
const meta = ref<AssetMeta | null>(null)
const categoryFlat = ref<{ categoryCode: string; label: string }[]>([])

function flattenCategories(nodes: CategoryNode[], level: number, out: { categoryCode: string; label: string }[]) {
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
    const tree = (await fetchCategoryTree()) || []
    const flat: { categoryCode: string; label: string }[] = []
    flattenCategories(tree, 0, flat)
    categoryFlat.value = flat
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- 物料盘点下拉（仓库 / 物料 / 物料分类） ---------------- */
const warehouseOpts = ref<WarehouseOption[]>([])
const materialOpts = ref<MaterialOption[]>([])
const materialOptsLoading = ref(false)
const materialMeta = ref<MaterialMeta | null>(null)

async function loadWarehouseOpts() {
  try {
    warehouseOpts.value = await fetchWarehouseOptions()
  } catch {
    warehouseOpts.value = []
  }
}

async function loadMaterialOpts(keyword?: string) {
  materialOptsLoading.value = true
  try {
    materialOpts.value = await fetchMaterialOptions({ keyword, size: 100 })
  } catch {
    materialOpts.value = []
  } finally {
    materialOptsLoading.value = false
  }
}

async function loadMaterialMeta() {
  try {
    materialMeta.value = await fetchMaterialMeta()
  } catch {
    materialMeta.value = null
  }
}

/* ---------------- 状态展示 ---------------- */
const STATUS_OPTIONS = ['盘点中', '已完成', '已取消']

function taskTagType(status?: string): TagType {
  if (status === '已完成') return 'success'
  if (status === '已取消') return 'info'
  return 'warning'
}

function resultTagType(result?: string): TagType {
  if (result === '一致') return 'success'
  if (result === '盘亏') return 'danger'
  if (result === '盘盈' || result === '错位') return 'warning'
  return 'info'
}

/** 明细筛选与录入可选的结果 */
const ITEM_RESULTS = ['一致', '盘亏', '盘盈', '错位']

/* ---------------- 列表查询 ---------------- */
const query = reactive({
  page: 1,
  size: 20,
  status: '',
  keyword: '',
})
const total = ref(0)
const records = ref<InventoryTaskItem[]>([])
const listLoading = ref(false)

async function load() {
  listLoading.value = true
  try {
    const res = await fetchInventoryTasks({
      page: query.page,
      size: query.size,
      status: query.status || undefined,
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
  query.status = ''
  query.keyword = ''
  search()
}

/* ---------------- 创建任务 ---------------- */
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  inspector: '',
  planFinish: '',
  targetType: 'ASSET' as InventoryTargetType,
  regionCodes: [] as string[],
  statusBiz: [] as string[],
  categoryCodes: [] as string[],
  roomCodes: [] as string[],
  warehouseCodes: [] as string[],
  materialCodes: [] as string[],
})

const isMaterialTask = computed(() => form.targetType === 'MATERIAL')

const rules: FormRules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  inspector: [{ required: true, message: '请输入盘点人', trigger: 'blur' }],
}

/** 机房编码远程搜索（取机柜 rackCode） */
const rackCodeOptions = ref<string[]>([])
const rackCodeLoading = ref(false)
let rackCodeTimer: number | undefined

async function loadRackCodes(keyword: string) {
  rackCodeLoading.value = true
  try {
    const res = await fetchRacks({ page: 1, size: 50, keyword: keyword || undefined })
    rackCodeOptions.value = (res.records ?? []).map((r) => r.rackCode)
  } catch {
    rackCodeOptions.value = []
  } finally {
    rackCodeLoading.value = false
  }
}

function onRackCodeSearch(keyword: string) {
  window.clearTimeout(rackCodeTimer)
  rackCodeTimer = window.setTimeout(() => void loadRackCodes(keyword), 300)
}

function onRackCodeFocus() {
  void loadRackCodes('')
}

function resetForm() {
  Object.assign(form, {
    name: '',
    inspector: '',
    planFinish: '',
    targetType: 'ASSET' as InventoryTargetType,
    regionCodes: [],
    statusBiz: [],
    categoryCodes: [],
    roomCodes: [],
    warehouseCodes: [],
    materialCodes: [],
  })
}

function onTargetTypeChange() {
  // 切换盘点对象时清掉另一套筛选，避免提交到后端后被静默忽略
  form.categoryCodes = []
  form.regionCodes = []
  form.statusBiz = []
  form.roomCodes = []
  form.warehouseCodes = []
  form.materialCodes = []
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const materialTask = form.targetType === 'MATERIAL'
    await createInventoryTask({
      name: form.name.trim(),
      inspector: form.inspector.trim(),
      planFinish: form.planFinish || undefined,
      targetType: form.targetType,
      filter: materialTask
        ? {
            warehouseCodes: form.warehouseCodes.length ? form.warehouseCodes : undefined,
            categoryCodes: form.categoryCodes.length ? form.categoryCodes : undefined,
            materialCodes: form.materialCodes.length ? form.materialCodes : undefined,
          }
        : {
            regionCodes: form.regionCodes.length ? form.regionCodes : undefined,
            roomCodes: form.roomCodes.length ? form.roomCodes : undefined,
            categoryCodes: form.categoryCodes.length ? form.categoryCodes : undefined,
            statusBiz: form.statusBiz.length ? form.statusBiz : undefined,
          },
    })
    ElMessage.success('创建成功')
    dialogVisible.value = false
    void load()
  } catch {
    // 拦截器已提示（无匹配资产/物料 400 中文消息）
  } finally {
    saving.value = false
  }
}

/* ---------------- 盘点明细抽屉 ---------------- */
const drawerVisible = ref(false)
const currentTaskNo = ref('')
const currentTask = ref<InventoryTaskItem | null>(null)
const taskLoading = ref(false)
const itemQuery = reactive({ page: 1, size: 10, result: '' })
const itemTotal = ref(0)
const items = ref<InventoryItemDetail[]>([])
const itemsLoading = ref(false)

const isRunning = computed(() => currentTask.value?.status === '盘点中')

/** 当前抽屉里的任务是否为物料盘点（决定明细列与录入表单） */
const isMaterialDetail = computed(() => currentTask.value?.targetType === 'MATERIAL')

function filterSummary(t: InventoryTaskItem | null): string {
  const f = t?.filter
  if (!f) return '不限'
  const parts: string[] = []
  if (f.regionCodes?.length) parts.push(`区域：${f.regionCodes.join('、')}`)
  if (f.statusBiz?.length) parts.push(`业务状态：${f.statusBiz.join('、')}`)
  if (f.categoryCodes?.length) parts.push(`分类：${f.categoryCodes.join('、')}`)
  if (f.roomCodes?.length) parts.push(`机房：${f.roomCodes.join('、')}`)
  if (f.warehouseCodes?.length) parts.push(`仓库：${f.warehouseCodes.join('、')}`)
  if (f.materialCodes?.length) parts.push(`物料：${f.materialCodes.join('、')}`)
  return parts.length ? parts.join('　|　') : '不限'
}

async function loadCurrentTask() {
  taskLoading.value = true
  try {
    currentTask.value = await fetchInventoryTaskDetail(currentTaskNo.value)
  } catch {
    // 拦截器已提示
  } finally {
    taskLoading.value = false
  }
}

async function loadItems() {
  itemsLoading.value = true
  try {
    const res = await fetchInventoryItems(currentTaskNo.value, {
      page: itemQuery.page,
      size: itemQuery.size,
      result: itemQuery.result || undefined,
    })
    itemTotal.value = res.total ?? 0
    items.value = res.records ?? []
  } catch {
    // 拦截器已提示
  } finally {
    itemsLoading.value = false
  }
}

function openDetail(row: InventoryTaskItem) {
  currentTaskNo.value = row.taskNo
  currentTask.value = row
  itemQuery.page = 1
  itemQuery.result = ''
  drawerVisible.value = true
  void loadCurrentTask()
  void loadItems()
}

function searchItems() {
  itemQuery.page = 1
  void loadItems()
}

function locationText(row: InventoryItemDetail): string {
  const e = row.expected
  if (!e || (!e.rackCode && !e.unitNo)) return '-'
  return [e.rackCode || '-', e.unitNo ? `U${e.unitNo}` : '-'].join(' / ')
}

/** 物料明细的账面数量（来自创建时的快照） */
function expectedQty(row: InventoryItemDetail | null | undefined): number | undefined {
  const q = row?.expected?.qty
  return q === undefined || q === null ? undefined : Number(q)
}

/** 物料明细的账面位置：仓库（+ 创建时若填了货位则一并展示） */
function materialLocation(row: InventoryItemDetail): string {
  return row.warehouseCode || '-'
}

/* ---------------- 录入结果 ---------------- */
const entryVisible = ref(false)
const entrySaving = ref(false)
const entryFormRef = ref<FormInstance>()
const entryItem = ref<InventoryItemDetail | null>(null)
const entryForm = reactive({
  result: '一致',
  diffNote: '',
  countedQty: undefined as number | undefined,
})

/** 物料明细：以实盘数量为准，结果可留空（「自动」）由服务端推导；资产明细仍必选四态 */
const entryRules = computed<FormRules>(() => ({
  result: isMaterialDetail.value
    ? []
    : [{ required: true, message: '请选择盘点结果', trigger: 'change' }],
  countedQty: isMaterialDetail.value
    ? [{ required: true, message: '请输入实盘数量', trigger: 'blur' }]
    : [],
}))

function openEntry(row: InventoryItemDetail) {
  entryItem.value = row
  const material = isMaterialDetail.value
  entryForm.result =
    row.result && row.result !== '未盘' ? row.result : material ? '' : '一致'
  entryForm.diffNote = row.diffNote || ''
  entryForm.countedQty = row.countedQty
  entryVisible.value = true
}

async function submitEntry() {
  if (!entryItem.value) return
  const valid = await entryFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  const material = isMaterialDetail.value
  if (material && (entryForm.countedQty ?? -1) < 0) {
    ElMessage.warning('实盘数量不能为负数')
    return
  }
  entrySaving.value = true
  try {
    await updateInventoryItem(currentTaskNo.value, entryItem.value.itemNo, {
      // 物料明细 result 可为空，服务端按 countedQty vs 账面 qty 推导 一致/盘亏/盘盈
      result: entryForm.result || undefined,
      diffNote: entryForm.diffNote || undefined,
      countedQty: material ? entryForm.countedQty : undefined,
    })
    ElMessage.success('录入成功')
    entryVisible.value = false
    void loadItems()
    void loadCurrentTask()
  } catch {
    // 拦截器已提示
  } finally {
    entrySaving.value = false
  }
}

/* ---------------- 完成 / 取消任务 ---------------- */
async function completeTask() {
  try {
    await ElMessageBox.confirm(
      `确定完成任务「${currentTask.value?.taskNo || ''}」吗？完成后不可再录入结果。`,
      '完成确认',
      { type: 'warning', confirmButtonText: '完成', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await completeInventoryTask(currentTaskNo.value)
    ElMessage.success('任务已完成')
    void loadCurrentTask()
    void load()
  } catch {
    // 拦截器已提示（尚有 N 条未盘 400 中文消息）
  }
}

async function cancelTask() {
  try {
    await ElMessageBox.confirm(
      `确定取消任务「${currentTask.value?.taskNo || ''}」吗？取消后不可恢复。`,
      '取消确认',
      { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '返回' },
    )
  } catch {
    return
  }
  try {
    await cancelInventoryTask(currentTaskNo.value)
    ElMessage.success('任务已取消')
    void loadCurrentTask()
    void load()
  } catch {
    // 拦截器已提示（已完成 400 中文消息）
  }
}

onMounted(() => {
  void load()
  void loadMeta()
  void loadCategories()
  void loadWarehouseOpts()
  void loadMaterialOpts('')
  void loadMaterialMeta()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">盘点管理</div>

    <!-- 顶部筛选 -->
    <div class="page-toolbar">
      <el-select
        v-model="query.status"
        placeholder="状态"
        clearable
        style="width: 130px"
        @change="search"
      >
        <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="任务编号 / 名称 / 盘点人"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="openCreate">创建任务</el-button>
    </div>

    <!-- 列表 -->
    <el-table v-loading="listLoading" :data="records" border stripe row-key="taskNo">
      <el-table-column prop="taskNo" label="任务编号" min-width="160" fixed="left" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="对象" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.targetType === 'MATERIAL' ? 'primary' : 'info'" size="small">
            {{ row.targetType === 'MATERIAL' ? '物料' : '资产' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="inspector" label="盘点人" width="100">
        <template #default="{ row }">{{ row.inspector || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="taskTagType(row.status)" size="small">{{ row.status || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="totalCount" label="明细数" width="90" align="right" />
      <el-table-column prop="checkedCount" label="已盘" width="80" align="right" />
      <el-table-column label="异常数" width="80" align="right">
        <template #default="{ row }">
          <span :class="{ 'diff-count': row.diffCount > 0 }">{{ row.diffCount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="planFinish" label="计划完成" width="110">
        <template #default="{ row }">{{ row.planFinish || '-' }}</template>
      </el-table-column>
      <el-table-column prop="finishTime" label="实际完成" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.finishTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">盘点明细</el-button>
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

    <!-- 创建任务 -->
    <el-dialog v-model="dialogVisible" title="创建盘点任务" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" placeholder="必填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="盘点人" prop="inspector">
              <el-input v-model="form.inspector" placeholder="必填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划完成">
              <el-date-picker
                v-model="form.planFinish"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="盘点对象">
              <el-radio-group v-model="form.targetType" @change="onTargetTypeChange">
                <el-radio value="ASSET">资产</el-radio>
                <el-radio value="MATERIAL">物料</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col v-if="!isMaterialTask" :span="12">
            <el-form-item label="区域">
              <el-select v-model="form.regionCodes" multiple clearable placeholder="不限" style="width: 100%">
                <el-option v-for="r in meta?.regions || []" :key="r.key" :label="r.label" :value="r.key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="!isMaterialTask" :span="12">
            <el-form-item label="业务状态">
              <el-select v-model="form.statusBiz" multiple clearable filterable placeholder="不限" style="width: 100%">
                <el-option v-for="s in meta?.bizStates || []" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="!isMaterialTask" :span="12">
            <el-form-item label="资产分类">
              <el-select v-model="form.categoryCodes" multiple clearable filterable placeholder="不限" style="width: 100%">
                <el-option
                  v-for="c in categoryFlat"
                  :key="c.categoryCode"
                  :label="c.label"
                  :value="c.categoryCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="!isMaterialTask" :span="24">
            <el-form-item label="机房编码">
              <el-select
                v-model="form.roomCodes"
                multiple
                clearable
                filterable
                remote
                :remote-method="onRackCodeSearch"
                :loading="rackCodeLoading"
                placeholder="输入机柜编码搜索，可多选，不限可留空"
                style="width: 100%"
                @focus="onRackCodeFocus"
              >
                <el-option v-for="c in rackCodeOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>

          <!-- 物料盘点：按分仓库存（物料 × 仓库）生成明细 -->
          <el-col v-if="isMaterialTask" :span="24">
            <el-form-item label="仓库">
              <el-select
                v-model="form.warehouseCodes"
                multiple
                clearable
                filterable
                placeholder="不限 = 全部有库存的仓库"
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
          </el-col>
          <el-col v-if="isMaterialTask" :span="12">
            <el-form-item label="物料分类">
              <el-select v-model="form.categoryCodes" multiple clearable filterable placeholder="不限" style="width: 100%">
                <el-option
                  v-for="c in materialMeta?.categories || []"
                  :key="c"
                  :label="c"
                  :value="c"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="isMaterialTask" :span="12">
            <el-form-item label="物料">
              <el-select
                v-model="form.materialCodes"
                multiple
                clearable
                filterable
                remote
                :remote-method="loadMaterialOpts"
                :loading="materialOptsLoading"
                placeholder="不限"
                style="width: 100%"
                @focus="() => loadMaterialOpts('')"
              >
                <el-option
                  v-for="m in materialOpts"
                  :key="m.materialCode"
                  :label="`${m.name}（${m.materialCode}）`"
                  :value="m.materialCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="filter-hint">
          {{ isMaterialTask ? '筛选条件均可留空 = 盘点全部分仓库存' : '筛选条件均可留空 = 盘点全部资产' }}
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">创建</el-button>
      </template>
    </el-dialog>

    <!-- 盘点明细抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="`盘点明细${currentTask ? `（${currentTask.taskNo}）` : ''}`"
      size="72%"
      destroy-on-close
    >
      <div class="drawer-top" v-loading="taskLoading">
        <template v-if="currentTask">
          <div class="drawer-head">
            <div class="drawer-head-title">
              {{ currentTask.name }}
              <el-tag :type="taskTagType(currentTask.status)" size="small" style="margin-left: 8px">
                {{ currentTask.status }}
              </el-tag>
            </div>
            <div v-if="isRunning" class="drawer-head-actions">
              <el-button type="success" @click="completeTask">完成任务</el-button>
              <el-button type="danger" plain @click="cancelTask">取消任务</el-button>
            </div>
          </div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="任务编号">{{ currentTask.taskNo }}</el-descriptions-item>
            <el-descriptions-item label="盘点人">{{ currentTask.inspector || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ currentTask.creator || '-' }}</el-descriptions-item>
            <el-descriptions-item label="明细数">{{ currentTask.totalCount }}</el-descriptions-item>
            <el-descriptions-item label="已盘">{{ currentTask.checkedCount }}</el-descriptions-item>
            <el-descriptions-item label="异常数">{{ currentTask.diffCount }}</el-descriptions-item>
            <el-descriptions-item label="计划完成">{{ currentTask.planFinish || '-' }}</el-descriptions-item>
            <el-descriptions-item label="实际完成">{{ currentTask.finishTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ currentTask.createdAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="筛选条件" :span="3">
              {{ filterSummary(currentTask) }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </div>

      <div class="drawer-toolbar">
        <el-select
          v-model="itemQuery.result"
          placeholder="盘点结果"
          clearable
          style="width: 140px"
          @change="searchItems"
        >
          <el-option v-for="r in ITEM_RESULTS" :key="r" :label="r" :value="r" />
        </el-select>
        <span class="drawer-toolbar-tip">共 {{ itemTotal }} 条明细</span>
      </div>

      <el-table v-loading="itemsLoading" :data="items" border stripe>
        <template v-if="isMaterialDetail">
          <el-table-column prop="materialCode" label="物料编码" min-width="130" fixed="left" show-overflow-tooltip />
          <el-table-column label="账面仓库" min-width="130">
            <template #default="{ row }">{{ materialLocation(row) }}</template>
          </el-table-column>
          <el-table-column label="账面数量" width="100" align="right">
            <template #default="{ row }">{{ expectedQty(row) ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="实盘数量" width="100" align="right">
            <template #default="{ row }">{{ row.countedQty ?? '-' }}</template>
          </el-table-column>
        </template>
        <template v-else>
          <el-table-column prop="assetNo" label="资产编号" min-width="130" fixed="left" show-overflow-tooltip />
          <el-table-column label="账面位置" min-width="130">
            <template #default="{ row }">{{ locationText(row) }}</template>
          </el-table-column>
          <el-table-column label="SN" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.expected?.sn || '-' }}</template>
          </el-table-column>
          <el-table-column label="业务状态" width="100">
            <template #default="{ row }">{{ row.expected?.statusBiz || '-' }}</template>
          </el-table-column>
        </template>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="resultTagType(row.result)" size="small">{{ row.result || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checker" label="盘点人" width="100">
          <template #default="{ row }">{{ row.checker || '-' }}</template>
        </el-table-column>
        <el-table-column prop="checkedAt" label="盘点时间" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.checkedAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="diffNote" label="差异说明" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.diffNote || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="isRunning" link type="primary" @click="openEntry(row)">录入结果</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="itemQuery.page"
          v-model:page-size="itemQuery.size"
          :total="itemTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadItems"
          @size-change="() => { itemQuery.page = 1; loadItems() }"
        />
      </div>

      <!-- 录入结果 -->
      <el-dialog v-model="entryVisible" title="录入盘点结果" width="480px" append-to-body destroy-on-close>
        <div class="entry-asset">
          {{ isMaterialDetail ? `${entryItem?.materialCode || ''} @ ${entryItem?.warehouseCode || ''}` : entryItem?.assetNo }}
        </div>
        <el-form ref="entryFormRef" :model="entryForm" :rules="entryRules" label-width="92px">
          <el-form-item v-if="isMaterialDetail" label="账面数量">
            <span class="entry-qty">{{ expectedQty(entryItem) ?? '-' }}</span>
          </el-form-item>
          <el-form-item v-if="isMaterialDetail" label="实盘数量" prop="countedQty">
            <el-input-number
              v-model="entryForm.countedQty"
              :min="0"
              controls-position="right"
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item label="结果" prop="result">
            <el-radio-group v-model="entryForm.result">
              <el-radio v-if="isMaterialDetail" value="">自动</el-radio>
              <el-radio v-for="r in ITEM_RESULTS" :key="r" :value="r">{{ r }}</el-radio>
            </el-radio-group>
            <div v-if="isMaterialDetail" class="entry-tip">
              「自动」按实盘数量 vs 账面数量推导 一致 / 盘亏 / 盘盈；盘盈亏完成任务时自动登记库存调整
            </div>
          </el-form-item>
          <el-form-item label="差异说明">
            <el-input
              v-model="entryForm.diffNote"
              type="textarea"
              :rows="3"
              placeholder="差异说明（选填）"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="entryVisible = false">取消</el-button>
          <el-button type="primary" :loading="entrySaving" @click="submitEntry">提交</el-button>
        </template>
      </el-dialog>
    </el-drawer>
  </div>
</template>

<style scoped>
.diff-count {
  color: #f56c6c;
  font-weight: 600;
}

.filter-hint {
  margin-left: 92px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.drawer-top {
  margin-bottom: 14px;
}

.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.drawer-head-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.drawer-head-actions {
  display: flex;
  gap: 8px;
}

.drawer-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.drawer-toolbar-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.entry-asset {
  margin-bottom: 12px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.entry-qty {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.entry-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
