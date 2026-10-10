<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import {
  createRack,
  deleteRack,
  fetchRackImportTemplate,
  fetchRackUnits,
  fetchRacks,
  fetchRooms,
  importRacks,
  updateRack,
} from '@/api/room'
import type { RackItem, RackUnit, RoomItem } from '@/api/room'
import { saveBlob } from '@/api/asset'
import type { CsvImportResult } from '@/api/types'
import { checkCsvImportSize } from '@/utils/upload'

const router = useRouter()

function openElevation(row: RackItem) {
  router.push({ path: '/racks/view', query: { code: row.rackCode } })
}

/* ---------------- 列表查询 ---------------- */
const query = reactive({ page: 1, size: 20, keyword: '', roomCode: '' })
const total = ref(0)
const records = ref<RackItem[]>([])
const listLoading = ref(false)
const roomOptions = ref<RoomItem[]>([])

async function load() {
  listLoading.value = true
  try {
    const res = await fetchRacks({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      roomCode: query.roomCode || undefined,
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
  query.roomCode = ''
  search()
}

async function loadRoomOptions() {
  try {
    const res = await fetchRooms({ page: 1, size: 200 })
    roomOptions.value = res.records ?? []
  } catch {
    // 拦截器已提示
  }
}

function roomLabel(roomCode?: string): string {
  if (!roomCode) return '-'
  const room = roomOptions.value.find((r) => r.roomCode === roomCode)
  return room ? `${roomCode}（${room.name}）` : roomCode
}

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const editingRackCode = ref('')
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  rackCode: '',
  roomCode: '',
  locationName: '',
  rowNo: undefined as number | undefined,
  colNo: undefined as number | undefined,
  orientation: '',
  uCapacity: 42,
  powerLimitKw: undefined as number | undefined,
  pduCount: undefined as number | undefined,
  coolingType: '',
  manager: '',
  remark: '',
})

const rules = {
  rackCode: [{ required: true, message: '请输入机柜编号', trigger: 'blur' }],
  roomCode: [{ required: true, message: '请选择所属机房', trigger: 'change' }],
}

function resetForm() {
  Object.assign(form, {
    rackCode: '',
    roomCode: '',
    locationName: '',
    rowNo: undefined,
    colNo: undefined,
    orientation: '',
    uCapacity: 42,
    powerLimitKw: undefined,
    pduCount: undefined,
    coolingType: '',
    manager: '',
    remark: '',
  })
}

function openCreate() {
  editingRackCode.value = ''
  resetForm()
  dialogVisible.value = true
  void loadRoomOptions()
}

function openEdit(row: RackItem) {
  editingRackCode.value = row.rackCode
  Object.assign(form, {
    rackCode: row.rackCode,
    roomCode: row.roomCode || '',
    locationName: row.locationName || '',
    rowNo: row.rowNo,
    colNo: row.colNo,
    orientation: row.orientation || '',
    uCapacity: row.uCapacity ?? 42,
    powerLimitKw: row.powerLimitKw,
    pduCount: row.pduCount,
    coolingType: row.coolingType || '',
    manager: row.manager || '',
    remark: row.remark || '',
  })
  dialogVisible.value = true
  void loadRoomOptions()
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      roomCode: form.roomCode,
      locationName: form.locationName || undefined,
      rowNo: form.rowNo,
      colNo: form.colNo,
      orientation: form.orientation || undefined,
      uCapacity: form.uCapacity,
      powerLimitKw: form.powerLimitKw,
      pduCount: form.pduCount,
      coolingType: form.coolingType || undefined,
      manager: form.manager || undefined,
      remark: form.remark || undefined,
    }
    if (editingRackCode.value) {
      await updateRack(editingRackCode.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createRack({ ...payload, rackCode: form.rackCode.trim() })
      ElMessage.success('创建成功，已自动生成 U 位')
    }
    dialogVisible.value = false
    void load()
  } catch {
    // 拦截器已提示（缩容被占 409 等）
  } finally {
    saving.value = false
  }
}

async function remove(row: RackItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除机柜「${row.rackCode}」吗？该操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteRack(row.rackCode)
    ElMessage.success('删除成功')
    void load()
  } catch {
    // 拦截器已提示（有占用 / 被资产引用 409 中文消息）
  }
}

/* ---------------- U 位平面图 ---------------- */
interface UnitRow {
  unitNo: number
  status: string
  occupiedBy?: string
  assetName?: string
  assetStatusBiz?: string
}

const planVisible = ref(false)
const planLoading = ref(false)
const planRackCode = ref('')
const planCapacity = ref(0)
const planRows = ref<UnitRow[]>([])

function statusColor(status?: string): string {
  const s = status || '空闲'
  if (s === '占用') return '#409eff'
  if (s === '预留') return '#e6a23c'
  if (s === '故障') return '#f56c6c'
  return '#67c23a'
}

async function openPlan(row: RackItem) {
  planRackCode.value = row.rackCode
  planCapacity.value = row.uCapacity ?? 0
  planRows.value = []
  planVisible.value = true
  planLoading.value = true
  try {
    const res = await fetchRackUnits(row.rackCode)
    const unitMap = new Map<number, RackUnit>()
    for (const u of res.units || []) unitMap.set(u.unitNo, u)
    const capacity = res.uCapacity ?? row.uCapacity ?? 0
    planCapacity.value = capacity
    const rows: UnitRow[] = []
    for (let n = capacity; n >= 1; n--) {
      const u = unitMap.get(n)
      rows.push({
        unitNo: n,
        status: u?.status || '空闲',
        occupiedBy: u?.occupiedBy,
        assetName: u?.assetName,
        assetStatusBiz: u?.assetStatusBiz,
      })
    }
    planRows.value = rows
  } catch {
    // 拦截器已提示
  } finally {
    planLoading.value = false
  }
}

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
    const blob = await fetchRackImportTemplate()
    saveBlob(blob, '机柜导入模板.csv')
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
    importResult.value = await importRacks(importFile.value)
    ElMessage.success('导入完成')
    void load()
    void loadRoomOptions()
  } catch {
    // 拦截器已提示
  } finally {
    importing.value = false
  }
}

const route = useRoute()

onMounted(() => {
  // 全局搜索深链：?keyword=xxx 直接带入筛选
  if (typeof route.query.keyword === 'string') query.keyword = route.query.keyword
  void load()
  void loadRoomOptions()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">机柜管理</div>

    <!-- 顶部筛选 -->
    <div class="page-toolbar">
      <el-select
        v-model="query.roomCode"
        placeholder="所属机房"
        clearable
        filterable
        style="width: 200px"
        @change="search"
      >
        <el-option
          v-for="r in roomOptions"
          :key="r.roomCode"
          :label="`${r.roomCode}（${r.name}）`"
          :value="r.roomCode"
        />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="机柜编号 / 位置名称"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" plain @click="search">查询</el-button>      <el-button @click="resetQuery">重置</el-button>
      <div style="flex: 1"></div>
      <el-button @click="openImport">导入</el-button>
      <el-button type="primary" @click="openCreate">新增机柜</el-button>
    </div>

    <!-- 列表 -->
    <el-table v-loading="listLoading" :data="records" border stripe row-key="rackCode">
      <el-table-column prop="rackCode" label="机柜编号" min-width="110" fixed="left" />
      <el-table-column label="机房" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ roomLabel(row.roomCode) }}</template>
      </el-table-column>
      <el-table-column prop="locationName" label="位置名称" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.locationName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="uCapacity" label="U位容量" width="90" />
      <el-table-column label="已用U" width="90">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.usedUnits ?? 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="供电上限(kW)" width="115">
        <template #default="{ row }">{{ row.powerLimitKw ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="manager" label="负责人" width="100">
        <template #default="{ row }">{{ row.manager || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openElevation(row)">可视化</el-button>
          <el-button link type="primary" @click="openPlan(row)">U位平面图</el-button>
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

    <!-- 新增 / 编辑机柜 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingRackCode ? '编辑机柜' : '新增机柜'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="机柜编号" prop="rackCode">
              <el-input
                v-model="form.rackCode"
                :disabled="!!editingRackCode"
                placeholder="唯一编码，创建后不可修改"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属机房" prop="roomCode">
              <el-select
                v-model="form.roomCode"
                filterable
                placeholder="选择机房"
                style="width: 100%"
              >
                <el-option
                  v-for="r in roomOptions"
                  :key="r.roomCode"
                  :label="`${r.roomCode}（${r.name}）`"
                  :value="r.roomCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="位置名称">
              <el-input v-model="form.locationName" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="行号">
              <el-input-number
                v-model="form.rowNo"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="列号">
              <el-input-number
                v-model="form.colNo"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="朝向">
              <el-input v-model="form.orientation" placeholder="如：正面朝通道" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="U位容量">
              <el-input-number
                v-model="form.uCapacity"
                :min="1"
                :max="100"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供电上限(kW)">
              <el-input-number
                v-model="form.powerLimitKw"
                :min="0"
                :precision="2"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="PDU 数量">
              <el-input-number
                v-model="form.pduCount"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="制冷方式">
              <el-input v-model="form.coolingType" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-input v-model="form.manager" />
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

    <!-- U 位平面图 -->
    <el-dialog
      v-model="planVisible"
      :title="`U位平面图 - ${planRackCode}（${planCapacity}U）`"
      width="620px"
      destroy-on-close
    >
      <div v-loading="planLoading">
        <div class="plan-legend">
          <span class="legend-item"><i class="legend-dot" style="background: #67c23a"></i>空闲</span>
          <span class="legend-item"><i class="legend-dot" style="background: #409eff"></i>占用</span>
          <span class="legend-item"><i class="legend-dot" style="background: #e6a23c"></i>预留</span>
          <span class="legend-item"><i class="legend-dot" style="background: #f56c6c"></i>故障</span>
        </div>
        <div class="plan-list">
          <div v-for="r in planRows" :key="r.unitNo" class="plan-row">
            <span class="plan-no">U{{ r.unitNo }}</span>
            <span
              class="plan-block"
              :style="{ background: statusColor(r.status) }"
              :title="r.status"
            ></span>
            <span class="plan-info" :class="{ 'is-occupied': r.status === '占用' }">
              <template v-if="r.status === '占用'">
                <span class="plan-asset">{{ r.assetName || r.occupiedBy || '-' }}</span>
                <el-tag v-if="r.assetStatusBiz" size="small" type="info">
                  {{ r.assetStatusBiz }}
                </el-tag>
              </template>
              <template v-else>{{ r.status }}</template>
            </span>
          </div>
          <el-empty v-if="!planLoading && !planRows.length" description="暂无 U 位数据" :image-size="60" />
        </div>
      </div>
    </el-dialog>

    <!-- 批量导入机柜 -->
    <el-dialog v-model="importVisible" title="批量导入机柜" width="620px" destroy-on-close>
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
        仅支持 CSV（UTF-8 带 BOM 更佳）；机柜编号、机房编码必填，机房须已存在（先导入机房）；U容量留空默认 42 且须为正整数，导入时按 U容量自动生成 1..n 号 U 位；重复机柜编号报错跳过，其余行照常导入（只增不改）。
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
.plan-legend {
  display: flex;
  gap: 16px;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.legend-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

.plan-list {
  max-height: 440px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 6px 10px;
}

.plan-row {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 22px;
  font-size: 12px;
  line-height: 22px;
}

.plan-row + .plan-row {
  border-top: 1px dashed #f0f2f5;
}

.plan-no {
  width: 40px;
  flex-shrink: 0;
  text-align: right;
  color: var(--el-text-color-regular);
  font-family: Consolas, Monaco, monospace;
}

.plan-block {
  width: 32px;
  height: 14px;
  flex-shrink: 0;
  border-radius: 2px;
}

.plan-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.plan-info.is-occupied {
  color: var(--el-text-color-primary);
}

.plan-asset {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
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
</style>
