<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import type { CsvImportResult, PageResult } from '@/api/types'
import {
  createMaterial,
  deleteMaterial,
  fetchMaterialImportTemplate,
  fetchMaterialMeta,
  fetchMaterials,
  fetchLayerOptions,
  fetchShelves,
  fetchWarehouseOptions,
  importMaterials,
  updateMaterial,
} from '@/api/warehouse'
import type { MaterialItem, MaterialMeta, WarehouseLayerItem, WarehouseOption } from '@/api/warehouse'
import { saveBlob } from '@/api/asset'
import { checkCsvImportSize } from '@/utils/upload'

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

/* ---------------- 元数据 ---------------- */
const meta = ref<MaterialMeta | null>(null)

async function loadMeta() {
  try {
    meta.value = await fetchMaterialMeta()
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- 货位三级级联（仓库 → 货架 → 层位） ---------------- */
const warehouseOpts = ref<WarehouseOption[]>([])
const shelfOpts = ref<{ shelfCode: string; name?: string; shelfType?: string }[]>([])
const layerOpts = ref<WarehouseLayerItem[]>([])
const cascadeLoading = ref(false)

async function loadWarehouseOpts() {
  try {
    // 带上停用仓，编辑历史货位时才不会因为下拉缺项而丢数据
    warehouseOpts.value = await fetchWarehouseOptions(true)
  } catch {
    warehouseOpts.value = []
  }
}

async function loadShelvesOf(warehouseCode?: string) {
  if (!warehouseCode) {
    shelfOpts.value = []
    return
  }
  try {
    shelfOpts.value = await fetchShelves(warehouseCode)
  } catch {
    shelfOpts.value = []
  }
}

async function loadLayersOf(shelfCode?: string) {
  if (!shelfCode) {
    layerOpts.value = []
    return
  }
  try {
    layerOpts.value = await fetchLayerOptions(shelfCode)
  } catch {
    layerOpts.value = []
  }
}

/** 选仓清空下级；选架清空层位 */
async function onWarehouseChange(v?: string) {
  form.shelfCode = ''
  form.layerCode = ''
  cascadeLoading.value = true
  try {
    await loadShelvesOf(v)
  } finally {
    cascadeLoading.value = false
  }
}

async function onShelfChange(v?: string) {
  form.layerCode = ''
  cascadeLoading.value = true
  try {
    await loadLayersOf(v)
  } finally {
    cascadeLoading.value = false
  }
}

/** 存放位置展示：新数据用 仓库/货架/层位，老数据回退自由文本 location */
function locationText(row: MaterialItem): string {
  const codes = [row.warehouseCode, row.shelfCode, row.layerCode].filter(Boolean)
  if (codes.length) return codes.join(' / ')
  return row.location || ''
}

function stockBreak(row: MaterialItem): string {
  const m = row.warehouseStocks
  if (!m || !Object.keys(m).length) return ''
  return Object.entries(m)
    .map(([k, v]) => `${k}:${v}`)
    .join('  ')
}

/* ---------------- 列表查询 ---------------- */
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  category: '',
})
const total = ref(0)
const records = ref<MaterialItem[]>([])
const listLoading = ref(false)

async function load() {
  listLoading.value = true
  try {
    const res = await fetchMaterials({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      category: query.category || undefined,
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
  query.category = ''
  search()
}

/** 库存低于安全库存时红色高亮提示补货 */
function isLowStock(row: MaterialItem): boolean {
  return row.safetyStock !== undefined && row.safetyStock !== null && row.stock < row.safetyStock
}

function fmtNum(v?: number): string {
  return v === undefined || v === null ? '-' : String(v)
}

function fmtPrice(v?: number): string {
  return v === undefined || v === null ? '-' : String(v)
}

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const editingCode = ref('')
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  materialCode: '',
  name: '',
  unit: '',
  category: '',
  type: '',
  brand: '',
  model: '',
  pn: '',
  spec: '',
  safetyStock: undefined as number | undefined,
  maxStock: undefined as number | undefined,
  price: undefined as number | undefined,
  warehouseCode: '',
  shelfCode: '',
  layerCode: '',
  remark: '',
  // 编辑时只读展示，不可修改
  stock: 0,
  totalIn: 0,
  totalOut: 0,
  warehouseStocks: {} as Record<string, number>,
})

const rules = computed<FormRules>(() => ({
  materialCode: [
    {
      required: !editingCode.value,
      message: '请输入物料编码',
      trigger: 'blur',
    },
  ],
  name: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  unit: [{ required: true, message: '请选择单位', trigger: 'change' }],
}))

function resetForm() {
  Object.assign(form, {
    materialCode: '',
    name: '',
    unit: '',
    category: '',
    type: '',
    brand: '',
    model: '',
    pn: '',
    spec: '',
    safetyStock: undefined,
    maxStock: undefined,
    price: undefined,
    warehouseCode: '',
    shelfCode: '',
    layerCode: '',
    remark: '',
    stock: 0,
    totalIn: 0,
    totalOut: 0,
    warehouseStocks: {},
  })
  shelfOpts.value = []
  layerOpts.value = []
}

function openCreate() {
  editingCode.value = ''
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: MaterialItem) {
  editingCode.value = row.materialCode
  Object.assign(form, {
    materialCode: row.materialCode,
    name: row.name || '',
    unit: row.unit || '',
    category: row.category || '',
    type: row.type || '',
    brand: row.brand || '',
    model: row.model || '',
    pn: row.pn || '',
    spec: row.spec || '',
    safetyStock: row.safetyStock,
    maxStock: row.maxStock,
    price: row.price,
    warehouseCode: row.warehouseCode || '',
    shelfCode: row.shelfCode || '',
    layerCode: row.layerCode || '',
    remark: row.remark || '',
    stock: row.stock,
    totalIn: row.totalIn,
    totalOut: row.totalOut,
    warehouseStocks: row.warehouseStocks || {},
  })
  // 回填下级下拉，保证三级选中项都能显示出来
  void loadShelvesOf(form.warehouseCode).then(() => loadLayersOf(form.shelfCode))
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      unit: form.unit,
      category: form.category || undefined,
      type: form.type || undefined,
      brand: form.brand || undefined,
      model: form.model || undefined,
      pn: form.pn || undefined,
      spec: form.spec || undefined,
      safetyStock: form.safetyStock,
      maxStock: form.maxStock,
      price: form.price,
      warehouseCode: form.warehouseCode || undefined,
      shelfCode: form.shelfCode || undefined,
      layerCode: form.layerCode || undefined,
      remark: form.remark || undefined,
    }
    if (editingCode.value) {
      await updateMaterial(editingCode.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createMaterial({ ...payload, materialCode: form.materialCode.trim() })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    void load()
  } catch {
    // 拦截器已提示（unit / category 不合法等后端中文报错）
  } finally {
    saving.value = false
  }
}

async function remove(row: MaterialItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除物料「${row.materialCode} ${row.name}」吗？该操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteMaterial(row.materialCode)
    ElMessage.success('删除成功')
    void load()
  } catch {
    // 拦截器已提示（有库存或有流水时 409 中文消息）
  }
}

onMounted(() => {
  void load()
  void loadMeta()
  void loadWarehouseOpts()
})

/* ---------------- 导入 ---------------- */
const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)
const importResult = ref<CsvImportResult | null>(null)

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
    const blob = await fetchMaterialImportTemplate()
    saveBlob(blob, '物料导入模板.csv')
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
    importResult.value = await importMaterials(importFile.value)
    ElMessage.success('导入完成')
    void load()
  } catch {
    // 拦截器已提示
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <div class="page-card">
    <div class="page-title">物料管理</div>

    <!-- 顶部筛选 -->
    <div class="page-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="编码 / 名称 / 品牌 / 型号"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select
        v-model="query.category"
        placeholder="分类"
        clearable
        filterable
        style="width: 160px"
        @change="search"
      >
        <el-option v-for="c in meta?.categories || []" :key="c" :label="c" :value="c" />
      </el-select>
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <div style="flex: 1"></div>
      <el-button @click="openImport">导入</el-button>
      <el-button type="primary" @click="openCreate">新增物料</el-button>
    </div>

    <!-- 列表 -->
    <el-table v-loading="listLoading" :data="records" border stripe row-key="materialCode">
      <el-table-column prop="materialCode" label="编码" min-width="130" fixed="left" />
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="category" label="分类" min-width="100" show-overflow-tooltip>
        <template #default="{ row }">{{ row.category || '-' }}</template>
      </el-table-column>
      <el-table-column label="品牌型号" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ [row.brand, row.model].filter(Boolean).join(' ') || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="unit" label="单位" width="70">
        <template #default="{ row }">{{ row.unit || '-' }}</template>
      </el-table-column>
      <el-table-column label="实时库存" width="100" align="right">
        <template #default="{ row }">
          <el-tooltip :disabled="!isLowStock(row)" content="低于安全库存，请补货" placement="top">
            <span :class="{ 'stock-low': isLowStock(row) }">{{ row.stock }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="安全库存" width="90" align="right">
        <template #default="{ row }">{{ fmtNum(row.safetyStock) }}</template>
      </el-table-column>
      <el-table-column label="最高库存" width="90" align="right">
        <template #default="{ row }">{{ fmtNum(row.maxStock) }}</template>
      </el-table-column>
      <el-table-column label="累计入" width="90" align="right">
        <template #default="{ row }">{{ fmtNum(row.totalIn) }}</template>
      </el-table-column>
      <el-table-column label="累计出" width="90" align="right">
        <template #default="{ row }">{{ fmtNum(row.totalOut) }}</template>
      </el-table-column>
      <el-table-column label="存放位置" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ locationText(row) || '-' }}</template>
      </el-table-column>
      <el-table-column label="分仓库存" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ stockBreak(row) || '-' }}</template>
      </el-table-column>
      <el-table-column label="参考单价" width="100" align="right">
        <template #default="{ row }">{{ fmtPrice(row.price) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
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

    <!-- 新增 / 编辑物料 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingCode ? '编辑物料' : '新增物料'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="编码" prop="materialCode">
              <el-input v-model="form.materialCode" :disabled="!!editingCode" placeholder="必填，创建后不可修改" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" placeholder="必填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位" prop="unit">
              <el-select v-model="form.unit" filterable placeholder="必选" style="width: 100%">
                <el-option v-for="u in meta?.units || []" :key="u" :label="u" :value="u" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="form.category" clearable filterable placeholder="选择分类" style="width: 100%">
                <el-option v-for="c in meta?.categories || []" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型">
              <el-input v-model="form.type" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-input v-model="form.brand" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号">
              <el-input v-model="form.model" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="PN">
              <el-input v-model="form.pn" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格">
              <el-input v-model="form.spec" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安全库存">
              <el-input-number v-model="form.safetyStock" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最高库存">
              <el-input-number v-model="form.maxStock" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="参考单价">
              <el-input-number v-model="form.price" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="存放位置">
              <div class="cascade">
                <el-select
                  v-model="form.warehouseCode"
                  clearable
                  filterable
                  placeholder="仓库"
                  class="cascade-item"
                  @change="onWarehouseChange"
                >
                  <el-option
                    v-for="w in warehouseOpts"
                    :key="w.warehouseCode"
                    :label="`${w.warehouseCode} ${w.name}`"
                    :value="w.warehouseCode"
                  />
                </el-select>
                <el-select
                  v-model="form.shelfCode"
                  clearable
                  filterable
                  :disabled="!form.warehouseCode || cascadeLoading"
                  placeholder="货架"
                  class="cascade-item"
                  @change="onShelfChange"
                >
                  <el-option
                    v-for="s in shelfOpts"
                    :key="s.shelfCode"
                    :label="`${s.shelfCode} ${s.name || ''}`"
                    :value="s.shelfCode"
                  />
                </el-select>
                <el-select
                  v-model="form.layerCode"
                  clearable
                  filterable
                  :disabled="!form.shelfCode || cascadeLoading"
                  placeholder="层位"
                  class="cascade-item"
                >
                  <el-option
                    v-for="l in layerOpts"
                    :key="l.layerCode"
                    :label="`${l.layerCode} ${l.name || ''}`"
                    :value="l.layerCode"
                  />
                </el-select>
              </div>
              <div class="form-tip">只选到仓库即为该仓默认货位；选架/层可精确到具体层位</div>
            </el-form-item>
          </el-col>
          <el-col v-if="editingCode" :span="24">
            <el-form-item label="库存信息">
              <span class="stock-readonly">
                实时库存 <b>{{ form.stock }}</b>
                <el-divider direction="vertical" />
                累计入 <b>{{ form.totalIn }}</b>
                <el-divider direction="vertical" />
                累计出 <b>{{ form.totalOut }}</b>
                <span class="stock-tip">（由出入库流水自动维护，不可编辑）</span>
              </span>
            </el-form-item>
            <el-form-item v-if="Object.keys(form.warehouseStocks).length" label="分仓明细">
              <span class="stock-readonly">
                <el-tag
                  v-for="(v, k) in form.warehouseStocks"
                  :key="k"
                  size="small"
                  :type="v > 0 ? 'success' : 'info'"
                  style="margin-right: 6px"
                >{{ k }}: {{ v }}</el-tag>
              </span>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="importVisible" title="批量导入物料" width="620px" destroy-on-close>
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
      <div class="import-hint">单位必填且须在单位字典中；分类可空但须在分类字典中；库存导入后为 0。</div>
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
  </div>
</template>

<style scoped>
.stock-low {
  color: #f56c6c;
  font-weight: 600;
}

.stock-readonly b {
  color: var(--el-text-color-primary);
}

.stock-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.import-actions {
  margin: 10px 0;
}

.import-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.cascade {
  display: flex;
  gap: 8px;
  width: 100%;
}

.cascade-item {
  flex: 1;
  min-width: 0;
}

.form-tip {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}
</style>
