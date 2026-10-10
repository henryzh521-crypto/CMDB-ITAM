<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createSupplier,
  deleteSupplier,
  fetchSuppliers,
  updateSupplier,
} from '@/api/suppliers'
import type { SupplierItem } from '@/api/suppliers'
import type { PageResult } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
/** v3.4：删除需角色显式授予（后端同样校验） */
const canDelete = computed(() => auth.canDelete('suppliers'))
const canWrite = computed(() => auth.canWrite('suppliers'))

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: '合作中',
  INACTIVE: '停用',
}

const STATUS_TAG: Record<string, 'success' | 'danger' | 'info'> = {
  ACTIVE: 'success',
  INACTIVE: 'danger',
}

const TYPE_OPTIONS = ['设备', '备件', '服务', '租赁']

/* ---------------- 列表查询 ---------------- */
const query = reactive({ page: 1, size: 20, keyword: '', status: '', type: '' })
const total = ref(0)
const records = ref<SupplierItem[]>([])
const listLoading = ref(false)

async function load() {
  listLoading.value = true
  try {
    const res: PageResult<SupplierItem> = await fetchSuppliers({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      type: query.type || undefined,
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
  query.type = ''
  search()
}

function statusLabel(status?: string) {
  if (!status) return '-'
  return STATUS_LABELS[status] || status
}

function statusTag(status?: string): 'success' | 'danger' | 'info' {
  return STATUS_TAG[status || ''] || 'info'
}

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const editingCode = ref('')
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  supplierCode: '',
  name: '',
  type: '',
  contact: '',
  phone: '',
  email: '',
  hotline: '',
  products: '',
  address: '',
  status: 'ACTIVE',
  remark: '',
})

const rules = computed<FormRules>(() => ({
  supplierCode: [{ required: !editingCode.value, message: '请输入供应商编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
}))

function openCreate() {
  if (!canWrite.value) return
  editingCode.value = ''
  Object.assign(form, {
    supplierCode: '',
    name: '',
    type: '',
    contact: '',
    phone: '',
    email: '',
    hotline: '',
    products: '',
    address: '',
    status: 'ACTIVE',
    remark: '',
  })
  dialogVisible.value = true
}

function openEdit(row: SupplierItem) {
  if (!canWrite.value) return
  editingCode.value = row.supplierCode
  Object.assign(form, {
    supplierCode: row.supplierCode || '',
    name: row.name || '',
    type: row.type || '',
    contact: row.contact || '',
    phone: row.phone || '',
    email: row.email || '',
    hotline: row.hotline || '',
    products: row.products || '',
    address: row.address || '',
    status: row.status || 'ACTIVE',
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return

  const payload = {
    name: form.name.trim(),
    type: form.type || undefined,
    contact: form.contact || undefined,
    phone: form.phone || undefined,
    email: form.email || undefined,
    hotline: form.hotline || undefined,
    products: form.products || undefined,
    address: form.address || undefined,
    status: form.status || undefined,
    remark: form.remark || undefined,
  }

  saving.value = true
  try {
    if (editingCode.value) {
      await updateSupplier(editingCode.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createSupplier({ supplierCode: form.supplierCode.trim(), ...payload })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    void load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function remove(row: SupplierItem) {
  if (!canWrite.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除供应商「${row.supplierCode} ${row.name}」吗？该操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteSupplier(row.supplierCode)
    ElMessage.success('删除成功')
    void load()
  } catch {
    // 拦截器已提示（被物料引用 409 等）
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">供应商列表</div>

    <div class="page-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="编码 / 名称 / 联系人 / 电话"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select
        v-model="query.type"
        placeholder="类型"
        clearable
        style="width: 120px"
        @change="search"
      >
        <el-option v-for="t in TYPE_OPTIONS" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select
        v-model="query.status"
        placeholder="状态"
        clearable
        style="width: 120px"
        @change="search"
      >
        <el-option label="合作中" value="ACTIVE" />
        <el-option label="停用" value="INACTIVE" />
      </el-select>
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <div style="flex: 1"></div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新增供应商</el-button>
    </div>

    <el-table v-loading="listLoading" :data="records" border stripe row-key="supplierCode">
      <el-table-column prop="supplierCode" label="供应商编码" min-width="130" fixed="left" />
      <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="90">
        <template #default="{ row }">{{ row.type || '-' }}</template>
      </el-table-column>
      <el-table-column prop="contact" label="联系人" width="100">
        <template #default="{ row }">{{ row.contact || '-' }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="电话" width="130">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.email || '-' }}</template>
      </el-table-column>
      <el-table-column prop="hotline" label="热线" width="130">
        <template #default="{ row }">{{ row.hotline || '-' }}</template>
      </el-table-column>
      <el-table-column prop="products" label="主营产品" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.products || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '-' }}</template>
      </el-table-column>
      <el-table-column v-if="canWrite" label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canDelete" link type="danger" @click="remove(row)">删除</el-button>
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

    <el-dialog
      v-model="dialogVisible"
      :title="editingCode ? '编辑供应商' : '新增供应商'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="供应商编码" prop="supplierCode">
              <el-input v-model="form.supplierCode" :disabled="!!editingCode" placeholder="创建后不可修改" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="form.type" clearable placeholder="选择类型" style="width: 100%">
                <el-option v-for="t in TYPE_OPTIONS" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="合作中" value="ACTIVE" />
                <el-option label="停用" value="INACTIVE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="form.contact" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电话">
              <el-input v-model="form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="热线">
              <el-input v-model="form.hotline" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="主营产品">
              <el-input v-model="form.products" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址">
              <el-input v-model="form.address" />
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
  </div>
</template>
