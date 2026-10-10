<script setup lang="ts">
import { reactive, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import {
  createRoom,
  deleteRoom,
  fetchRoomImportTemplate,
  fetchRooms,
  importRooms,
  updateRoom,
} from '@/api/room'
import { saveBlob } from '@/api/asset'
import type { RoomItem } from '@/api/room'
import type { CsvImportResult } from '@/api/types'
import { checkCsvImportSize } from '@/utils/upload'

const router = useRouter()

function openFloor(row: RoomItem) {
  router.push({ path: '/rooms/floor', query: { code: row.roomCode } })
}

/* ---------------- 列表查询 ---------------- */
const query = reactive({ page: 1, size: 20, keyword: '' })
const total = ref(0)
const records = ref<RoomItem[]>([])
const listLoading = ref(false)

async function load() {
  listLoading.value = true
  try {
    const res = await fetchRooms({
      page: query.page,
      size: query.size,
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

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const editingRoomCode = ref('')
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  roomCode: '',
  name: '',
  dataCenter: '',
  city: '',
  location: '',
  areaSqm: undefined as number | undefined,
  loadKg: undefined as number | undefined,
  coolingKw: undefined as number | undefined,
  powerKw: undefined as number | undefined,
  abPath: '',
  envMonitor: '',
  manager: '',
  remark: '',
})

const rules = {
  roomCode: [{ required: true, message: '请输入机房编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入机房名称', trigger: 'blur' }],
}

function resetForm() {
  Object.assign(form, {
    roomCode: '',
    name: '',
    dataCenter: '',
    city: '',
    location: '',
    areaSqm: undefined,
    loadKg: undefined,
    coolingKw: undefined,
    powerKw: undefined,
    abPath: '',
    envMonitor: '',
    manager: '',
    remark: '',
  })
}

function openCreate() {
  editingRoomCode.value = ''
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: RoomItem) {
  editingRoomCode.value = row.roomCode
  Object.assign(form, {
    roomCode: row.roomCode,
    name: row.name || '',
    dataCenter: row.dataCenter || '',
    city: row.city || '',
    location: row.location || '',
    areaSqm: row.areaSqm,
    loadKg: row.loadKg,
    coolingKw: row.coolingKw,
    powerKw: row.powerKw,
    abPath: row.abPath || '',
    envMonitor: row.envMonitor || '',
    manager: row.manager || '',
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      dataCenter: form.dataCenter || undefined,
      city: form.city || undefined,
      location: form.location || undefined,
      areaSqm: form.areaSqm,
      loadKg: form.loadKg,
      coolingKw: form.coolingKw,
      powerKw: form.powerKw,
      abPath: form.abPath || undefined,
      envMonitor: form.envMonitor || undefined,
      manager: form.manager || undefined,
      remark: form.remark || undefined,
    }
    if (editingRoomCode.value) {
      await updateRoom(editingRoomCode.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createRoom({ ...payload, roomCode: form.roomCode.trim() })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    void load()
  } catch {
    // 拦截器已提示（重复 409 等）
  } finally {
    saving.value = false
  }
}

async function remove(row: RoomItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除机房「${row.roomCode} ${row.name}」吗？该操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteRoom(row.roomCode)
    ElMessage.success('删除成功')
    void load()
  } catch {
    // 拦截器已提示（有机柜 409 中文消息）
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
    const blob = await fetchRoomImportTemplate()
    saveBlob(blob, '机房导入模板.csv')
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
    importResult.value = await importRooms(importFile.value)
    ElMessage.success('导入完成')
    void load()
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
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">机房管理</div>

    <!-- 顶部筛选 -->
    <div class="page-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="机房编号 / 名称 / 数据中心"
        clearable
        style="width: 240px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" plain @click="search">查询</el-button>
      <div style="flex: 1"></div>
      <el-button @click="openImport">导入</el-button>
      <el-button type="primary" @click="openCreate">新增机房</el-button>
    </div>

    <!-- 列表 -->
    <el-table v-loading="listLoading" :data="records" border stripe row-key="roomCode">
      <el-table-column prop="roomCode" label="机房编号" min-width="110" fixed="left" />
      <el-table-column prop="name" label="机房名称" min-width="130" show-overflow-tooltip />
      <el-table-column prop="dataCenter" label="数据中心" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.dataCenter || '-' }}</template>
      </el-table-column>
      <el-table-column prop="city" label="城市" width="100">
        <template #default="{ row }">{{ row.city || '-' }}</template>
      </el-table-column>
      <el-table-column prop="cabinetCount" label="机柜数" width="90">
        <template #default="{ row }">{{ row.cabinetCount ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="manager" label="负责人" width="100">
        <template #default="{ row }">{{ row.manager || '-' }}</template>
      </el-table-column>
      <el-table-column prop="areaSqm" label="面积(㎡)" width="95">
        <template #default="{ row }">{{ row.areaSqm ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="powerKw" label="电力(kW)" width="95">
        <template #default="{ row }">{{ row.powerKw ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="coolingKw" label="制冷(kW)" width="95">
        <template #default="{ row }">{{ row.coolingKw ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openFloor(row)">平面图</el-button>
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

    <!-- 新增 / 编辑机房 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingRoomCode ? '编辑机房' : '新增机房'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="机房编号" prop="roomCode">
              <el-input
                v-model="form.roomCode"
                :disabled="!!editingRoomCode"
                placeholder="唯一编码，创建后不可修改"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机房名称" prop="name">
              <el-input v-model="form.name" placeholder="必填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据中心">
              <el-input v-model="form.dataCenter" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="城市">
              <el-input v-model="form.city" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="位置">
              <el-input v-model="form.location" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="面积(㎡)">
              <el-input-number
                v-model="form.areaSqm"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="承重(kg)">
              <el-input-number
                v-model="form.loadKg"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电力(kW)">
              <el-input-number
                v-model="form.powerKw"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="制冷(kW)">
              <el-input-number
                v-model="form.coolingKw"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="A/B 路径">
              <el-input v-model="form.abPath" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="环境监控">
              <el-input v-model="form.envMonitor" />
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

    <!-- 批量导入机房 -->
    <el-dialog v-model="importVisible" title="批量导入机房" width="620px" destroy-on-close>
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
        仅支持 CSV（UTF-8 带 BOM 更佳）；机房编码、机房名称必填，编码创建后不可修改；重复编码报错跳过，其余行照常导入，不修改已有机房；区域编码留空按数据中心自动推断。
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
