<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, InputInstance, UploadFile } from 'element-plus'
import { fetchAssetMeta, saveBlob } from '@/api/asset'
import type { AssetMeta } from '@/api/asset'
import { checkCsvImportSize } from '@/utils/upload'
import { fetchCategoryTree } from '@/api/masterdata'
import type { CategoryNode } from '@/api/masterdata'
import { fetchMaterialOptions } from '@/api/warehouse'
import type { MaterialOption } from '@/api/warehouse'
import {
  RFID_BATCH_STATUSES,
  RFID_OWNER_TYPES,
  RFID_SOURCE_TYPES,
  addRfidBatchLine,
  cancelRfidBatch,
  confirmRfidBatch,
  createRfidBatch,
  deleteRfidBatchLine,
  fetchRfidBatchDetail,
  fetchRfidBatchScans,
  fetchRfidBatches,
  importRfidBatchLines,
  scanRfidBatch,
  startRfidBatch,
  updateRfidBatchLine,
} from '@/api/rfid'
import type {
  RfidBatchDetail,
  RfidBatchItem,
  RfidBatchLine,
  RfidConfirmResult,
  RfidImportResult,
  RfidScanRecord,
} from '@/api/rfid'

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

/* ---------------- 元数据 / 分类 ---------------- */
const meta = ref<AssetMeta | null>(null)
const categoryFlat = ref<{ categoryCode: string; label: string }[]>([])

function flattenCategories(
  nodes: CategoryNode[],
  level: number,
  out: { categoryCode: string; label: string }[],
) {
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

/* ---------------- 状态展示 ---------------- */
const SCAN_STATUSES = ['已扫描', '已入库', '异常']

function batchTagType(status?: string): TagType {
  if (status === '已确认') return 'success'
  if (status === '扫描中') return 'warning'
  return 'info' // 草稿 / 已取消 灰色
}

function scanTagType(status?: string): TagType {
  if (status === '已入库') return 'success'
  if (status === '异常') return 'danger'
  return 'primary' // 已扫描 蓝
}

/* ---------------- 列表查询 ---------------- */
const query = reactive({
  page: 1,
  size: 20,
  status: '',
  keyword: '',
})
const total = ref(0)
const records = ref<RfidBatchItem[]>([])
const listLoading = ref(false)

async function load() {
  listLoading.value = true
  try {
    const res = await fetchRfidBatches({
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

/* ---------------- 创建批次 ---------------- */
const createVisible = ref(false)
const saving = ref(false)
const createFormRef = ref<FormInstance>()

const createForm = reactive({
  name: '',
  supplierCode: '',
  sourceType: '采购到货',
  remark: '',
})

const createRules: FormRules = {
  name: [{ required: true, message: '请输入批次名称', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(createForm, { name: '', supplierCode: '', sourceType: '采购到货', remark: '' })
  createVisible.value = true
}

async function saveCreate() {
  const valid = await createFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const res = await createRfidBatch({
      name: createForm.name.trim(),
      supplierCode: createForm.supplierCode.trim() || undefined,
      sourceType: createForm.sourceType || undefined,
      remark: createForm.remark.trim() || undefined,
    })
    ElMessage.success(`创建成功，批次号：${res.batchNo}`)
    createVisible.value = false
    void load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

/* ---------------- 批次详情抽屉 ---------------- */
const drawerVisible = ref(false)
const currentBatchNo = ref('')
const currentBatch = ref<RfidBatchDetail | null>(null)
const batchLoading = ref(false)

const scanQuery = reactive({ page: 1, size: 10, scanStatus: '' })
const scanTotal = ref(0)
const scans = ref<RfidScanRecord[]>([])
const scansLoading = ref(false)

const isDraft = computed(() => currentBatch.value?.status === '草稿')
const isScanning = computed(() => currentBatch.value?.status === '扫描中')

const expectedTotal = computed(() =>
  (currentBatch.value?.items || []).reduce((sum, i) => sum + (i.expectedQty || 0), 0),
)

async function loadCurrentBatch() {
  if (!currentBatchNo.value) return
  batchLoading.value = true
  try {
    currentBatch.value = await fetchRfidBatchDetail(currentBatchNo.value)
  } catch {
    // 拦截器已提示
  } finally {
    batchLoading.value = false
  }
}

async function loadScans() {
  if (!currentBatchNo.value) return
  scansLoading.value = true
  try {
    const res = await fetchRfidBatchScans(currentBatchNo.value, {
      page: scanQuery.page,
      size: scanQuery.size,
      scanStatus: scanQuery.scanStatus || undefined,
    })
    scanTotal.value = res.total ?? 0
    scans.value = res.records ?? []
  } catch {
    // 拦截器已提示
  } finally {
    scansLoading.value = false
  }
}

function openDetail(row: RfidBatchItem) {
  currentBatchNo.value = row.batchNo
  currentBatch.value = null
  scanQuery.page = 1
  scanQuery.scanStatus = ''
  scanFeedback.value = []
  drawerVisible.value = true
  void loadCurrentBatch()
  void loadScans()
}

function searchScans() {
  scanQuery.page = 1
  void loadScans()
}

function linePercent(row: RfidBatchLine): number {
  if (!row.expectedQty || row.expectedQty <= 0) return 0
  return Math.min(100, Math.round((row.receivedQty / row.expectedQty) * 100))
}

/* ---------------- 开始 / 取消批次 ---------------- */
async function startBatch() {
  try {
    await ElMessageBox.confirm(
      `确定开始扫描批次「${currentBatch.value?.batchNo || ''}」吗？开始后收货明细不可再修改。`,
      '开始扫描',
      { type: 'warning', confirmButtonText: '开始', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await startRfidBatch(currentBatchNo.value)
    ElMessage.success('批次已进入扫描中')
    void loadCurrentBatch()
    void load()
  } catch {
    // 拦截器已提示（无明细 400 中文消息）
  }
}

async function cancelBatch() {
  try {
    await ElMessageBox.confirm(
      `确定取消批次「${currentBatch.value?.batchNo || ''}」吗？取消后不可恢复。`,
      '取消确认',
      { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '返回' },
    )
  } catch {
    return
  }
  try {
    await cancelRfidBatch(currentBatchNo.value)
    ElMessage.success('批次已取消')
    void loadCurrentBatch()
    void load()
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- 添加明细（仅草稿） ---------------- */
const addLineVisible = ref(false)
const addSaving = ref(false)
const addFormRef = ref<FormInstance>()
const addForm = reactive({
  materialCode: '',
  expectedQty: 1,
})

const addRules: FormRules = {
  materialCode: [{ required: true, message: '请选择物料', trigger: 'change' }],
  expectedQty: [{ required: true, message: '请输入预期数量', trigger: 'blur' }],
}

const materialOptions = ref<MaterialOption[]>([])
const materialLoading = ref(false)
let materialTimer: number | undefined

async function loadMaterialOptions(keyword: string) {
  materialLoading.value = true
  try {
    const res = await fetchMaterialOptions({ keyword: keyword || undefined, size: 50 })
    materialOptions.value = res ?? []
  } catch {
    materialOptions.value = []
  } finally {
    materialLoading.value = false
  }
}

function onMaterialSearch(keyword: string) {
  window.clearTimeout(materialTimer)
  materialTimer = window.setTimeout(() => void loadMaterialOptions(keyword), 300)
}

function openAddLine() {
  addForm.materialCode = ''
  addForm.expectedQty = 1
  addLineVisible.value = true
  void loadMaterialOptions('')
}

async function submitAddLine() {
  const valid = await addFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  addSaving.value = true
  try {
    await addRfidBatchLine(currentBatchNo.value, {
      materialCode: addForm.materialCode,
      expectedQty: addForm.expectedQty,
    })
    ElMessage.success('明细已添加')
    addLineVisible.value = false
    void loadCurrentBatch()
    void load()
  } catch {
    // 拦截器已提示（重复物料 409 中文消息）
  } finally {
    addSaving.value = false
  }
}

/* ---------------- CSV 导入明细（仅草稿） ---------------- */
const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)
const importResult = ref<RfidImportResult | null>(null)

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

function downloadImportTemplate() {
  // 客户端生成两列模板（带 BOM，便于 Excel 打开中文不乱码）
  const content = '\uFEFF物料编码,预期数量\nMAT-001,10\n'
  saveBlob(new Blob([content], { type: 'text/csv;charset=utf-8' }), 'RFID明细导入模板.csv')
}

async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择 CSV 文件')
    return
  }
  if (!checkCsvImportSize(importFile.value)) return
  importing.value = true
  try {
    importResult.value = await importRfidBatchLines(currentBatchNo.value, importFile.value)
    ElMessage.success('导入完成')
    void loadCurrentBatch()
    void load()
  } catch {
    // 拦截器已提示
  } finally {
    importing.value = false
  }
}

/* ---------------- 编辑数量 / 删除明细（仅草稿） ---------------- */
const editLineVisible = ref(false)
const editSaving = ref(false)
const editLine = ref<RfidBatchLine | null>(null)
const editQty = ref(1)

function openEditLine(row: RfidBatchLine) {
  editLine.value = row
  editQty.value = row.expectedQty
  editLineVisible.value = true
}

async function submitEditLine() {
  if (!editLine.value) return
  if (!editQty.value || editQty.value < 1) {
    ElMessage.warning('预期数量必须 ≥ 1')
    return
  }
  editSaving.value = true
  try {
    await updateRfidBatchLine(currentBatchNo.value, editLine.value.id, {
      expectedQty: editQty.value,
    })
    ElMessage.success('预期数量已更新')
    editLineVisible.value = false
    void loadCurrentBatch()
    void load()
  } catch {
    // 拦截器已提示
  } finally {
    editSaving.value = false
  }
}

async function removeLine(row: RfidBatchLine) {
  try {
    await ElMessageBox.confirm(
      `确定删除明细「${row.materialCode}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteRfidBatchLine(currentBatchNo.value, row.id)
    ElMessage.success('明细已删除')
    void loadCurrentBatch()
    void load()
  } catch {
    // 拦截器已提示（已有收货 409 中文消息）
  }
}

/* ---------------- 扫描面板（仅扫描中） ---------------- */
const scanPanelVisible = ref(false)
const scanSubmitting = ref(false)
const epcInputRef = ref<InputInstance>()
const scanForm = reactive({
  epc: '',
  materialCode: '',
  sn: '',
})

interface ScanFeedback {
  key: number
  kind: 'ok' | 'dup' | 'err'
  epc: string
  text: string
}

const scanFeedback = ref<ScanFeedback[]>([])
let feedbackKey = 0

const batchMaterialCodes = computed(() =>
  (currentBatch.value?.items || []).map((i) => i.materialCode),
)

function pushFeedback(kind: ScanFeedback['kind'], epc: string, text: string) {
  feedbackKey += 1
  scanFeedback.value = [{ key: feedbackKey, kind, epc, text }, ...scanFeedback.value].slice(0, 5)
}

function openScanPanel() {
  scanForm.epc = ''
  scanForm.sn = ''
  scanPanelVisible.value = true
}

function onScanPanelOpened() {
  void nextTick(() => epcInputRef.value?.focus())
}

async function submitScan() {
  const epc = scanForm.epc.trim()
  if (!epc) {
    ElMessage.warning('请扫描或输入 EPC')
    return
  }
  scanSubmitting.value = true
  try {
    const res = await scanRfidBatch(currentBatchNo.value, {
      epc,
      sn: scanForm.sn.trim() || undefined,
      materialCode: scanForm.materialCode || undefined,
    })
    if (res.duplicate) {
      pushFeedback('dup', epc, '该 EPC 已扫描过（幂等）')
    } else if (res.scanStatus === '异常') {
      const parts = [res.exceptionType, res.exceptionNote].filter(Boolean)
      pushFeedback('err', epc, parts.length ? parts.join('：') : '扫描异常')
    } else {
      const parts: string[] = []
      if (res.materialCode) parts.push(`物料 ${res.materialCode}`)
      if (res.sn) parts.push(`SN ${res.sn}`)
      if (res.assetNo) parts.push(`生成资产 ${res.assetNo}`)
      pushFeedback('ok', epc, parts.length ? parts.join('，') : '扫描成功')
    }
    scanForm.epc = ''
    scanForm.sn = ''
    scanQuery.page = 1
    void loadScans()
    void loadCurrentBatch()
  } catch (e) {
    // 拦截器已弹后端中文消息；反馈区同步保留失败记录
    pushFeedback('err', epc, (e as Error)?.message || '扫描失败')
  } finally {
    scanSubmitting.value = false
    void nextTick(() => epcInputRef.value?.focus())
  }
}

/* ---------------- 确认入库（仅扫描中） ---------------- */
const confirmVisible = ref(false)
const confirmSaving = ref(false)
const confirmResult = ref<RfidConfirmResult | null>(null)
const confirmForm = reactive({
  namePrefix: '',
  categoryCode: '',
  modelCode: '',
  ownerType: 'SELF' as 'SELF' | 'LEASE',
  regionCode: '',
  remark: '',
})

function openConfirm() {
  Object.assign(confirmForm, {
    namePrefix: '',
    categoryCode: '',
    modelCode: '',
    ownerType: 'SELF',
    regionCode: '',
    remark: '',
  })
  confirmResult.value = null
  confirmVisible.value = true
}

async function submitConfirm() {
  confirmSaving.value = true
  try {
    confirmResult.value = await confirmRfidBatch(currentBatchNo.value, {
      namePrefix: confirmForm.namePrefix.trim() || undefined,
      categoryCode: confirmForm.categoryCode || undefined,
      modelCode: confirmForm.modelCode.trim() || undefined,
      ownerType: confirmForm.ownerType,
      regionCode: confirmForm.regionCode || undefined,
      remark: confirmForm.remark.trim() || undefined,
    })
    ElMessage.success('确认入库完成')
    void loadCurrentBatch()
    void loadScans()
    void load()
  } catch {
    // 拦截器已提示（非扫描中 409 中文消息）
  } finally {
    confirmSaving.value = false
  }
}

onMounted(() => {
  void load()
  void loadMeta()
  void loadCategories()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">RFID 批次验收</div>

    <!-- 顶部筛选 -->
    <div class="page-toolbar">
      <el-select
        v-model="query.status"
        placeholder="状态"
        clearable
        style="width: 130px"
        @change="search"
      >
        <el-option v-for="s in RFID_BATCH_STATUSES" :key="s" :label="s" :value="s" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="批次号 / 名称 / 供应商"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="openCreate">创建批次</el-button>
    </div>

    <!-- 批次列表 -->
    <el-table v-loading="listLoading" :data="records" border stripe row-key="batchNo">
      <el-table-column prop="batchNo" label="批次号" min-width="160" fixed="left" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="supplierCode" label="供应商" min-width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ row.supplierCode || '-' }}</template>
      </el-table-column>
      <el-table-column prop="sourceType" label="来源类型" width="100">
        <template #default="{ row }">{{ row.sourceType || '-' }}</template>
      </el-table-column>
      <el-table-column prop="itemCount" label="明细数" width="80" align="right" />
      <el-table-column prop="scannedCount" label="已扫描" width="80" align="right" />
      <el-table-column prop="confirmedCount" label="已入库" width="80" align="right" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="batchTagType(row.status)" size="small">{{ row.status || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="creator" label="创建人" width="100">
        <template #default="{ row }">{{ row.creator || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.createdAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

    <!-- 创建批次 -->
    <el-dialog v-model="createVisible" title="创建批次" width="560px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="92px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="createForm.name" placeholder="必填" />
        </el-form-item>
        <el-form-item label="供应商编码">
          <el-input v-model="createForm.supplierCode" placeholder="选填" />
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="createForm.sourceType" style="width: 100%">
            <el-option v-for="t in RFID_SOURCE_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 批次详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="`批次详情（${currentBatchNo}）`"
      size="76%"
      destroy-on-close
    >
      <div v-loading="batchLoading">
        <template v-if="currentBatch">
          <div class="drawer-head">
            <div class="drawer-head-title">
              {{ currentBatch.name }}
              <el-tag :type="batchTagType(currentBatch.status)" size="small" style="margin-left: 8px">
                {{ currentBatch.status }}
              </el-tag>
            </div>
            <div class="drawer-head-actions">
              <template v-if="isDraft">
                <el-button type="primary" @click="openAddLine">添加明细</el-button>
                <el-button @click="openImport">导入明细</el-button>
                <el-button type="success" @click="startBatch">开始扫描</el-button>
                <el-button type="danger" plain @click="cancelBatch">取消</el-button>
              </template>
              <template v-else-if="isScanning">
                <el-button type="primary" @click="openScanPanel">扫描面板</el-button>
                <el-button type="success" @click="openConfirm">确认入库</el-button>
                <el-button type="danger" plain @click="cancelBatch">取消</el-button>
              </template>
            </div>
          </div>

          <!-- 统计卡 -->
          <div class="stat-row">
            <div class="stat-card">
              <div class="stat-label">明细物料数</div>
              <div class="stat-value">{{ currentBatch.itemCount }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">预期总数</div>
              <div class="stat-value">{{ expectedTotal }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">已扫描</div>
              <div class="stat-value">{{ currentBatch.stats?.scanned ?? 0 }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">已入库</div>
              <div class="stat-value stat-success">{{ currentBatch.stats?.confirmed ?? 0 }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">异常</div>
              <div class="stat-value" :class="{ 'stat-danger': (currentBatch.stats?.abnormal ?? 0) > 0 }">
                {{ currentBatch.stats?.abnormal ?? 0 }}
              </div>
            </div>
          </div>

          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="批次号">{{ currentBatch.batchNo }}</el-descriptions-item>
            <el-descriptions-item label="供应商">{{ currentBatch.supplierCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="来源类型">{{ currentBatch.sourceType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ currentBatch.creator || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ currentBatch.createdAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="确认时间">{{ currentBatch.confirmedAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="3">{{ currentBatch.remark || '-' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 收货明细 -->
          <div class="drawer-subtitle">收货明细（{{ currentBatch.items?.length || 0 }}）</div>
          <el-table :data="currentBatch.items || []" border stripe size="small">
            <el-table-column prop="materialCode" label="物料编码" min-width="150" show-overflow-tooltip />
            <el-table-column prop="expectedQty" label="预期数量" width="100" align="right" />
            <el-table-column prop="receivedQty" label="已收货" width="100" align="right" />
            <el-table-column label="进度" min-width="180">
              <template #default="{ row }">
                <div class="progress-cell">
                  <el-progress
                    :percentage="linePercent(row)"
                    :stroke-width="8"
                    :show-text="false"
                    :status="linePercent(row) >= 100 ? 'success' : undefined"
                  />
                  <span class="progress-text">{{ row.receivedQty }}/{{ row.expectedQty }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="isDraft" label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditLine(row)">编辑</el-button>
                <el-button link type="danger" @click="removeLine(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 扫描记录 -->
          <div class="drawer-subtitle">扫描记录（{{ scanTotal }}）</div>
          <div class="drawer-toolbar">
            <el-select
              v-model="scanQuery.scanStatus"
              placeholder="扫描状态"
              clearable
              style="width: 140px"
              @change="searchScans"
            >
              <el-option v-for="s in SCAN_STATUSES" :key="s" :label="s" :value="s" />
            </el-select>
          </div>
          <el-table v-loading="scansLoading" :data="scans" border stripe size="small">
            <el-table-column prop="epc" label="EPC" min-width="180" show-overflow-tooltip />
            <el-table-column prop="sn" label="SN" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.sn || '-' }}</template>
            </el-table-column>
            <el-table-column prop="materialCode" label="物料" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialCode || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="scanTagType(row.scanStatus)" size="small">{{ row.scanStatus || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="exceptionType" label="异常类型" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.exceptionType || '-' }}</template>
            </el-table-column>
            <el-table-column prop="scanTime" label="时间" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.scanTime || '-' }}</template>
            </el-table-column>
            <el-table-column prop="operator" label="操作人" width="100">
              <template #default="{ row }">{{ row.operator || '-' }}</template>
            </el-table-column>
          </el-table>

          <div class="page-pagination">
            <el-pagination
              v-model:current-page="scanQuery.page"
              v-model:page-size="scanQuery.size"
              :total="scanTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="loadScans"
              @size-change="() => { scanQuery.page = 1; loadScans() }"
            />
          </div>
        </template>
      </div>

      <!-- 添加明细 -->
      <el-dialog
        v-model="addLineVisible"
        title="添加明细"
        width="520px"
        append-to-body
        destroy-on-close
      >
        <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="92px">
          <el-form-item label="物料" prop="materialCode">
            <el-select
              v-model="addForm.materialCode"
              filterable
              remote
              :remote-method="onMaterialSearch"
              :loading="materialLoading"
              placeholder="输入物料编码 / 名称搜索"
              style="width: 100%"
              @focus="loadMaterialOptions('')"
            >
              <el-option
                v-for="m in materialOptions"
                :key="m.materialCode"
                :label="`${m.materialCode}（${m.name}）`"
                :value="m.materialCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="预期数量" prop="expectedQty">
            <el-input-number v-model="addForm.expectedQty" :min="1" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="addLineVisible = false">取消</el-button>
          <el-button type="primary" :loading="addSaving" @click="submitAddLine">添加</el-button>
        </template>
      </el-dialog>

      <!-- CSV 导入明细 -->
      <el-dialog
        v-model="importVisible"
        title="导入明细"
        width="620px"
        append-to-body
        destroy-on-close
      >
        <div class="import-hint">
          CSV 两列格式：<b>物料编码,预期数量</b>（预期数量 ≥ 1），可先
          <el-button link type="primary" @click="downloadImportTemplate">下载模板</el-button>
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
          <el-table
            v-if="importResult.errors?.length"
            :data="importResult.errors"
            border
            size="small"
            style="margin-top: 8px"
          >
            <el-table-column prop="row" label="行号" width="80" />
            <el-table-column prop="reason" label="失败原因" min-width="240" show-overflow-tooltip />
          </el-table>
        </template>
      </el-dialog>

      <!-- 编辑预期数量 -->
      <el-dialog
        v-model="editLineVisible"
        title="编辑预期数量"
        width="440px"
        append-to-body
        destroy-on-close
      >
        <div class="edit-material">{{ editLine?.materialCode }}</div>
        <el-form label-width="92px">
          <el-form-item label="预期数量">
            <el-input-number v-model="editQty" :min="1" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editLineVisible = false">取消</el-button>
          <el-button type="primary" :loading="editSaving" @click="submitEditLine">保存</el-button>
        </template>
      </el-dialog>
    </el-drawer>

    <!-- 扫描面板 -->
    <el-dialog
      v-model="scanPanelVisible"
      :title="`扫描面板（${currentBatchNo}）`"
      width="600px"
      destroy-on-close
      @opened="onScanPanelOpened"
    >
      <el-form label-width="92px" @submit.prevent>
        <el-form-item label="EPC" required>
          <el-input
            ref="epcInputRef"
            v-model="scanForm.epc"
            size="large"
            class="epc-input"
            placeholder="扫描或输入 EPC，回车提交"
            clearable
            :disabled="scanSubmitting"
            @keyup.enter="submitScan"
          />
        </el-form-item>
        <el-form-item label="物料编码">
          <el-select
            v-model="scanForm.materialCode"
            clearable
            filterable
            placeholder="选填：限定本批次明细物料"
            style="width: 100%"
          >
            <el-option v-for="m in batchMaterialCodes" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="SN">
          <el-input v-model="scanForm.sn" placeholder="选填" clearable />
        </el-form-item>
      </el-form>

      <div class="scan-feedback">
        <div class="scan-feedback-head">扫描反馈（保留最近 5 条）</div>
        <el-empty v-if="!scanFeedback.length" description="等待扫码枪输入…" :image-size="48" />
        <div
          v-for="f in scanFeedback"
          :key="f.key"
          class="scan-feedback-item"
          :class="`is-${f.kind}`"
        >
          <span class="scan-feedback-icon">{{ f.kind === 'ok' ? '✓' : f.kind === 'dup' ? '⟳' : '✗' }}</span>
          <span class="scan-feedback-epc">{{ f.epc }}</span>
          <span class="scan-feedback-text">{{ f.text }}</span>
        </div>
      </div>

      <template #footer>
        <el-button @click="scanPanelVisible = false">关闭</el-button>
        <el-button type="primary" :loading="scanSubmitting" @click="submitScan">提交扫描</el-button>
      </template>
    </el-dialog>

    <!-- 确认入库 -->
    <el-dialog v-model="confirmVisible" title="确认入库" width="640px" destroy-on-close>
      <div class="confirm-hint">按以下默认模板为已扫描记录批量生成资产：</div>
      <el-form label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="资产名前缀">
              <el-input v-model="confirmForm.namePrefix" placeholder="选填，如：服务器-" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资产分类">
              <el-select v-model="confirmForm.categoryCode" clearable filterable placeholder="选择分类" style="width: 100%">
                <el-option
                  v-for="c in categoryFlat"
                  :key="c.categoryCode"
                  :label="c.label"
                  :value="c.categoryCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号编码">
              <el-input v-model="confirmForm.modelCode" placeholder="选填，文本" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="权属">
              <el-radio-group v-model="confirmForm.ownerType">
                <el-radio v-for="t in RFID_OWNER_TYPES" :key="t" :value="t">
                  {{ t === 'SELF' ? '自持' : '租赁' }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="区域">
              <el-select v-model="confirmForm.regionCode" clearable placeholder="选择区域" style="width: 100%">
                <el-option v-for="r in meta?.regions || []" :key="r.key" :label="r.label" :value="r.key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="confirmForm.remark" type="textarea" :rows="2" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template v-if="confirmResult">
        <el-alert
          type="success"
          :closable="false"
          :title="`成功生成 ${confirmResult.generated} 个资产，跳过 ${confirmResult.skipped} 条`"
          style="margin-top: 8px"
        />
        <el-table
          v-if="confirmResult.exceptions?.length"
          :data="confirmResult.exceptions"
          border
          size="small"
          style="margin-top: 8px"
        >
          <el-table-column prop="epc" label="EPC" min-width="180" show-overflow-tooltip />
          <el-table-column label="SN" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.sn || '-' }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
        </el-table>
      </template>

      <template #footer>
        <el-button @click="confirmVisible = false">关闭</el-button>
        <el-button v-if="!confirmResult" type="primary" :loading="confirmSaving" @click="submitConfirm">
          确认入库
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
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

.stat-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.stat-card {
  flex: 1;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: #fafbfc;
  padding: 10px 14px;
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stat-value {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.stat-success {
  color: var(--el-color-success);
}

.stat-danger {
  color: var(--el-color-danger);
}

.drawer-subtitle {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 18px 0 10px;
}

.drawer-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-text {
  font-size: 12px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.import-hint {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 10px;
}

.import-actions {
  margin: 10px 0;
}

.edit-material {
  margin-bottom: 12px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.epc-input :deep(.el-input__inner) {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
}

.scan-feedback {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px 12px;
  background: #fafbfc;
}

.scan-feedback-head {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.scan-feedback-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 5px 8px;
  border-radius: 4px;
  font-size: 13px;
}

.scan-feedback-item.is-ok {
  background: #f0f9eb;
  color: var(--el-color-success);
}

.scan-feedback-item.is-dup {
  background: #fdf6ec;
  color: var(--el-color-warning);
}

.scan-feedback-item.is-err {
  background: #fef0f0;
  color: var(--el-color-danger);
}

.scan-feedback-icon {
  font-weight: 700;
  flex-shrink: 0;
}

.scan-feedback-epc {
  font-weight: 600;
  word-break: break-all;
}

.scan-feedback-text {
  color: inherit;
  opacity: 0.9;
}

.confirm-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 10px;
}
</style>
