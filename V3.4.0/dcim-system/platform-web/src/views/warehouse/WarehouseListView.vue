<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createLayer,
  createShelf,
  createWarehouse,
  deleteLayer,
  deleteShelf,
  deleteWarehouse,
  fetchShelves,
  fetchWarehouseMeta,
  fetchWarehouses,
  fetchWarehouseStock,
  updateLayer,
  updateShelf,
  updateWarehouse,
} from '@/api/warehouse'
import type {
  LayerPayload,
  WarehouseItem,
  WarehouseLayerItem,
  WarehouseMeta,
  WarehouseShelfItem,
  WarehouseStockItem,
} from '@/api/warehouse'
import { fetchRooms } from '@/api/room'
import type { RoomItem } from '@/api/room'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
/** v3.4：删除需角色显式授予（后端同样校验） */
const canDelete = computed(() => auth.canDelete('warehouses'))
const canWrite = computed(() => !!auth.modules['warehouses']?.write)

/* ---------------- 元数据 ---------------- */
const meta = ref<WarehouseMeta | null>(null)
const rooms = ref<RoomItem[]>([])

async function loadMeta() {
  try {
    meta.value = await fetchWarehouseMeta()
  } catch {
    // 拦截器已提示
  }
  try {
    const res = await fetchRooms({ page: 1, size: 500 })
    rooms.value = res.records ?? []
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- 仓库列表 ---------------- */
const query = reactive({ page: 1, size: 20, keyword: '', status: '' as '' | '正常' | '停用' })
const total = ref(0)
const records = ref<WarehouseItem[]>([])
const listLoading = ref(false)

async function load() {
  listLoading.value = true
  try {
    const res = await fetchWarehouses({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
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
  query.status = ''
  search()
}

/* ---------------- 仓库新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const editingCode = ref('')
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  warehouseCode: '',
  name: '',
  warehouseType: '',
  roomCode: '',
  address: '',
  manager: '',
  capacityDesc: '',
  status: '正常',
  isDefault: false,
  remark: '',
  version: 0,
})

const rules = computed<FormRules>(() => ({
  name: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
  warehouseType: [{ required: true, message: '请选择仓库类型', trigger: 'change' }],
}))

function resetForm() {
  Object.assign(form, {
    warehouseCode: '',
    name: '',
    warehouseType: '',
    roomCode: '',
    address: '',
    manager: '',
    capacityDesc: '',
    status: '正常',
    isDefault: false,
    remark: '',
    version: 0,
  })
}

function openCreate() {
  editingCode.value = ''
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: WarehouseItem) {
  editingCode.value = row.warehouseCode
  Object.assign(form, {
    warehouseCode: row.warehouseCode,
    name: row.name || '',
    warehouseType: row.warehouseType || '',
    roomCode: row.roomCode || '',
    address: row.address || '',
    manager: row.manager || '',
    capacityDesc: row.capacityDesc || '',
    status: row.status || '正常',
    isDefault: row.isDefault,
    remark: row.remark || '',
    version: row.version || 0,
  })
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingCode.value) {
      await updateWarehouse(editingCode.value, {
        name: form.name.trim(),
        warehouseType: form.warehouseType,
        roomCode: form.roomCode,
        address: form.address,
        manager: form.manager,
        capacityDesc: form.capacityDesc,
        status: form.status,
        isDefault: form.isDefault,
        remark: form.remark,
        version: form.version,
      })
      ElMessage.success('保存成功')
    } else {
      await createWarehouse({
        warehouseCode: form.warehouseCode.trim() || undefined,
        name: form.name.trim(),
        warehouseType: form.warehouseType,
        roomCode: form.roomCode || undefined,
        address: form.address || undefined,
        manager: form.manager || undefined,
        capacityDesc: form.capacityDesc || undefined,
        status: form.status,
        isDefault: form.isDefault,
        remark: form.remark || undefined,
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    void load()
  } catch {
    // 拦截器已提示（机房不存在 / 编号重复 / 默认仓冲突 / 版本冲突）
  } finally {
    saving.value = false
  }
}

async function remove(row: WarehouseItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除仓库「${row.warehouseCode} ${row.name}」吗？该操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteWarehouse(row.warehouseCode)
    ElMessage.success('删除成功')
    void load()
  } catch {
    // 拦截器已提示（有货架 / 被引用 / 默认仓 409）
  }
}

/* ---------------- 货架 / 层位 ---------------- */
const shelfVisible = ref(false)
const shelfLoading = ref(false)
const current = ref<WarehouseItem | null>(null)
const shelves = ref<WarehouseShelfItem[]>([])

async function openShelves(row: WarehouseItem) {
  current.value = row
  shelfVisible.value = true
  await loadShelves()
}

async function loadShelves() {
  if (!current.value) return
  shelfLoading.value = true
  try {
    shelves.value = await fetchShelves(current.value.warehouseCode)
  } catch {
    shelves.value = []
  } finally {
    shelfLoading.value = false
  }
}

const shelfDialogVisible = ref(false)
const shelfSaving = ref(false)
const shelfRef = ref<FormInstance>()
const shelfForm = reactive({
  shelfCode: '',
  name: '',
  shelfType: '标准货架',
  layerCount: 4,
  status: '正常',
  remark: '',
  version: 0,
})
const shelfEditingCode = ref('')

const shelfRules: FormRules = {
  layerCount: [{ required: true, message: '请输入层数', trigger: 'blur' }],
}

function openShelfCreate() {
  shelfEditingCode.value = ''
  Object.assign(shelfForm, {
    shelfCode: '',
    name: '',
    shelfType: meta.value?.shelfTypes?.[0] || '标准货架',
    layerCount: 4,
    status: '正常',
    remark: '',
    version: 0,
  })
  shelfDialogVisible.value = true
}

function openShelfEdit(row: WarehouseShelfItem) {
  shelfEditingCode.value = row.shelfCode
  Object.assign(shelfForm, {
    shelfCode: row.shelfCode,
    name: row.name || '',
    shelfType: row.shelfType,
    layerCount: row.layerCount,
    status: row.status,
    remark: row.remark || '',
    version: row.version || 0,
  })
  shelfDialogVisible.value = true
}

async function saveShelf() {
  if (!current.value) return
  const valid = await shelfRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  shelfSaving.value = true
  try {
    if (shelfEditingCode.value) {
      await updateShelf(shelfEditingCode.value, {
        name: shelfForm.name,
        shelfType: shelfForm.shelfType,
        layerCount: shelfForm.layerCount,
        status: shelfForm.status,
        remark: shelfForm.remark,
        version: shelfForm.version,
      })
      ElMessage.success('保存成功')
    } else {
      await createShelf(current.value.warehouseCode, {
        shelfCode: shelfForm.shelfCode.trim() || undefined,
        name: shelfForm.name || undefined,
        shelfType: shelfForm.shelfType,
        layerCount: shelfForm.layerCount,
        status: shelfForm.status,
        remark: shelfForm.remark || undefined,
      })
      ElMessage.success('创建成功，层位已自动生成')
    }
    shelfDialogVisible.value = false
    await loadShelves()
    void load()
  } catch {
    // 拦截器已提示（编号重复 / 层数守卫 / 版本冲突）
  } finally {
    shelfSaving.value = false
  }
}

async function removeShelf(row: WarehouseShelfItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除货架「${row.shelfCode} ${row.name || ''}」吗？其下层位将一并删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteShelf(row.shelfCode)
    ElMessage.success('删除成功')
    await loadShelves()
    void load()
  } catch {
    // 拦截器已提示（被物料引用 409）
  }
}

async function addLayer(row: WarehouseShelfItem) {
  try {
    await createLayer(row.shelfCode, {})
    ElMessage.success('已新增层位')
    await loadShelves()
    void load()
  } catch {
    // 拦截器已提示
  }
}

const layerDialogVisible = ref(false)
const layerSaving = ref(false)
const layerForm = reactive({
  layerCode: '',
  shelfCode: '',
  name: '',
  capacityDesc: '',
  status: '正常',
  remark: '',
  version: 0,
})

function openLayerEdit(row: WarehouseLayerItem) {
  Object.assign(layerForm, {
    layerCode: row.layerCode,
    shelfCode: row.shelfCode,
    name: row.name || '',
    capacityDesc: row.capacityDesc || '',
    status: row.status,
    remark: row.remark || '',
    version: row.version || 0,
  })
  layerDialogVisible.value = true
}

async function saveLayer() {
  layerSaving.value = true
  const payload: LayerPayload = {
    name: layerForm.name,
    capacityDesc: layerForm.capacityDesc,
    status: layerForm.status,
    remark: layerForm.remark,
    version: layerForm.version,
  }
  try {
    await updateLayer(layerForm.layerCode, payload)
    ElMessage.success('保存成功')
    layerDialogVisible.value = false
    await loadShelves()
  } catch {
    // 拦截器已提示
  } finally {
    layerSaving.value = false
  }
}

async function removeLayer(row: WarehouseLayerItem) {
  try {
    await ElMessageBox.confirm(`确定删除层位「${row.layerCode}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteLayer(row.layerCode)
    ElMessage.success('删除成功')
    await loadShelves()
    void load()
  } catch {
    // 拦截器已提示（被物料引用 409）
  }
}

/* ---------------- 分仓库存 ---------------- */
const stockVisible = ref(false)
const stockLoading = ref(false)
const stockRows = ref<WarehouseStockItem[]>([])

async function openStock(row: WarehouseItem) {
  current.value = row
  stockVisible.value = true
  stockLoading.value = true
  try {
    stockRows.value = await fetchWarehouseStock(row.warehouseCode)
  } catch {
    stockRows.value = []
  } finally {
    stockLoading.value = false
  }
}

function alertText(a?: 'LOW' | 'OUT' | null): string {
  return a === 'OUT' ? '缺货' : a === 'LOW' ? '低储' : ''
}

onMounted(() => {
  void load()
  void loadMeta()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">仓库管理</div>

    <div class="page-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="编号 / 名称 / 负责人"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="search">
        <el-option v-for="s in meta?.statuses || ['正常', '停用']" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <div style="flex: 1"></div>
      <span class="code-rule">
        编码规则 仓库 {{ meta?.codeRules?.warehouse || 'WH-001' }} · 货架
        {{ meta?.codeRules?.shelf || 'SH-001' }} · 层位 {{ meta?.codeRules?.layer || 'LY-001' }}
      </span>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新增仓库</el-button>
    </div>

    <el-table v-loading="listLoading" :data="records" border stripe row-key="warehouseCode">
      <el-table-column prop="warehouseCode" label="编号" width="110" fixed="left" />
      <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="warehouseType" label="类型" width="120">
        <template #default="{ row }">{{ row.warehouseType || '-' }}</template>
      </el-table-column>
      <el-table-column prop="roomCode" label="关联机房" width="110">
        <template #default="{ row }">{{ row.roomCode || '-' }}</template>
      </el-table-column>
      <el-table-column prop="manager" label="负责人" width="100">
        <template #default="{ row }">{{ row.manager || '-' }}</template>
      </el-table-column>
      <el-table-column label="货架" width="70" align="right">
        <template #default="{ row }">{{ row.shelfCount }}</template>
      </el-table-column>
      <el-table-column label="层位" width="70" align="right">
        <template #default="{ row }">{{ row.layerCount }}</template>
      </el-table-column>
      <el-table-column label="物料数" width="80" align="right">
        <template #default="{ row }">{{ row.materialCount }}</template>
      </el-table-column>
      <el-table-column label="库存合计" width="90" align="right">
        <template #default="{ row }">{{ row.stockQty }}</template>
      </el-table-column>
      <el-table-column label="默认仓" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault" type="success" size="small">默认</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === '正常' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openShelves(row)">货架层位</el-button>
          <el-button link type="primary" @click="openStock(row)">库存</el-button>
          <template v-if="canWrite">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canDelete" link type="danger" @click="remove(row)">删除</el-button>
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

    <!-- 新增 / 编辑仓库 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingCode ? '编辑仓库' : '新增仓库'"
      width="680px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="编号">
              <el-input v-model="form.warehouseCode" :disabled="!!editingCode" placeholder="留空自动 WH-001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" placeholder="必填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="warehouseType">
              <el-select v-model="form.warehouseType" placeholder="必选" style="width: 100%">
                <el-option v-for="t in meta?.warehouseTypes || []" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联机房">
              <el-select v-model="form.roomCode" clearable filterable placeholder="可空" style="width: 100%">
                <el-option v-for="r in rooms" :key="r.roomCode" :label="`${r.roomCode} ${r.name}`" :value="r.roomCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-input v-model="form.manager" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="s in meta?.statuses || ['正常', '停用']" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址">
              <el-input v-model="form.address" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="容量说明">
              <el-input v-model="form.capacityDesc" placeholder="如：8 排货架 / 320 个层位" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认仓">
              <el-switch v-model="form.isDefault" active-text="是" inactive-text="否" />
              <div class="form-tip">全局仅一个默认仓；必须保留一个默认仓</div>
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

    <!-- 货架 / 层位 -->
    <el-dialog
      v-model="shelfVisible"
      :title="`货架层位 · ${current?.warehouseCode || ''} ${current?.name || ''}`"
      width="1080px"
      destroy-on-close
    >
      <div class="page-toolbar" style="margin-bottom: 10px">
        <span class="code-rule">层位由货架层数自动生成（1..N），编号 LY-### 只增不改</span>
        <div style="flex: 1"></div>
        <el-button v-if="canWrite" type="primary" @click="openShelfCreate">新增货架</el-button>
      </div>
      <el-table v-loading="shelfLoading" :data="shelves" border stripe row-key="shelfCode">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="layer-wrap">
              <div class="layer-head">
                <span>层位明细</span>
                <el-button v-if="canWrite" link type="primary" size="small" @click="addLayer(row)">
                  新增层位
                </el-button>
              </div>
              <el-table :data="row.layers" border size="small" row-key="layerCode">
                <el-table-column prop="layerCode" label="层位编号" width="110" />
                <el-table-column prop="layerNo" label="层序" width="70" align="center" />
                <el-table-column prop="name" label="名称" min-width="120">
                  <template #default="{ row: l }">{{ l.name || '-' }}</template>
                </el-table-column>
                <el-table-column prop="capacityDesc" label="容量" min-width="140">
                  <template #default="{ row: l }">{{ l.capacityDesc || '-' }}</template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="80" align="center" />
                <el-table-column label="操作" width="130" fixed="right">
                  <template #default="{ row: l }">
                    <template v-if="canWrite">
                      <el-button link type="primary" @click="openLayerEdit(l)">编辑</el-button>
                      <el-button v-if="canDelete" link type="danger" @click="removeLayer(l)">删除</el-button>
                    </template>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="shelfCode" label="编号" width="110" />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="shelfType" label="类型" width="110" />
        <el-table-column label="层数" width="80" align="right">
          <template #default="{ row }">{{ row.layerTotal }} / {{ row.layerCount }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="canWrite">
              <el-button link type="primary" @click="openShelfEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="addLayer(row)">加层</el-button>
              <el-button v-if="canDelete" link type="danger" @click="removeShelf(row)">删除</el-button>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 新增 / 编辑货架 -->
    <el-dialog
      v-model="shelfDialogVisible"
      :title="shelfEditingCode ? '编辑货架' : '新增货架'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="shelfRef" :model="shelfForm" :rules="shelfRules" label-width="84px">
        <el-form-item label="编号">
          <el-input v-model="shelfForm.shelfCode" :disabled="!!shelfEditingCode" placeholder="留空自动 SH-001" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="shelfForm.name" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="shelfForm.shelfType" style="width: 100%">
            <el-option v-for="t in meta?.shelfTypes || []" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="层数" prop="layerCount">
          <el-input-number
            v-model="shelfForm.layerCount"
            :min="1"
            :max="50"
            controls-position="right"
            style="width: 100%"
          />
          <div class="form-tip">
            {{ shelfEditingCode ? '扩容自动补层位；缩容需先删除超出行' : '创建时自动生成层位 1..N' }}
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="shelfForm.status" style="width: 100%">
            <el-option v-for="s in meta?.statuses || ['正常', '停用']" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="shelfForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shelfDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="shelfSaving" @click="saveShelf">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑层位 -->
    <el-dialog v-model="layerDialogVisible" :title="`编辑层位 ${layerForm.layerCode}`" width="520px" destroy-on-close>
      <el-form :model="layerForm" label-width="84px">
        <el-form-item label="编号">
          <el-input :model-value="layerForm.layerCode" disabled />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="layerForm.name" />
        </el-form-item>
        <el-form-item label="容量">
          <el-input v-model="layerForm.capacityDesc" placeholder="如：10U / 5 层抽屉" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="layerForm.status" style="width: 100%">
            <el-option v-for="s in meta?.layerStatuses || ['正常', '停用', '锁定']" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="layerForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="layerDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="layerSaving" @click="saveLayer">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分仓库存 -->
    <el-dialog
      v-model="stockVisible"
      :title="`库存明细 · ${current?.warehouseCode || ''} ${current?.name || ''}`"
      width="960px"
      destroy-on-close
    >
      <el-table v-loading="stockLoading" :data="stockRows" border stripe row-key="materialCode">
        <el-table-column prop="materialCode" label="物料编码" width="130" />
        <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="100">
          <template #default="{ row }">{{ row.category || '-' }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column label="现存量" width="90" align="right">
          <template #default="{ row }">
            <span :class="{ 'stock-low': row.alert }">{{ row.qty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="安全库存" width="90" align="right">
          <template #default="{ row }">{{ row.safetyStock ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="单价" width="90" align="right">
          <template #default="{ row }">{{ row.price ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="默认货位" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ [row.defaultWarehouse, row.shelfCode, row.layerCode].filter(Boolean).join(' / ') || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="预警" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.alert === 'OUT'" type="danger" size="small">缺货</el-tag>
            <el-tag v-else-if="row.alert === 'LOW'" type="warning" size="small">低储</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!stockLoading && !stockRows.length" class="empty-hint">该仓库暂无库存记录</div>
    </el-dialog>
  </div>
</template>

<style scoped>
.code-rule {
  margin-right: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.form-tip {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.layer-wrap {
  padding: 8px 16px 12px 44px;
}

.layer-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.stock-low {
  color: #f56c6c;
  font-weight: 600;
}

.empty-hint {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
