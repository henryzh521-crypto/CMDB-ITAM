<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createContract,
  deleteContract,
  fetchContracts,
  updateContract,
} from '@/api/suppliers'
import type { ContractItem } from '@/api/suppliers'
import type { PageResult } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
/** v3.4：删除需角色显式授予（后端同样校验） */
const canDelete = computed(() => auth.canDelete('contracts'))
const canWrite = computed(() => auth.canWrite('contracts'))

const TYPE_OPTIONS = ['采购', '销售', '融资租赁']
const STATUS_OPTIONS = ['生效', '到期', '终止', '草稿']

const STATUS_TAG: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  生效: 'success',
  到期: 'warning',
  终止: 'danger',
  草稿: 'info',
}

function statusTag(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  return STATUS_TAG[status || ''] || 'info'
}

function fmtAmount(v?: number | string): string {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function fmtRate(v?: number | string): string {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return `${(n * 100).toFixed(4)}%`
}

/* ---------------- 列表查询 ---------------- */
const query = reactive({ page: 1, size: 20, keyword: '', status: '', type: '' })
const total = ref(0)
const records = ref<ContractItem[]>([])
const listLoading = ref(false)

async function load() {
  listLoading.value = true
  try {
    const res: PageResult<ContractItem> = await fetchContracts({
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

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const editingNo = ref('')
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  contractNo: '',
  name: '',
  type: '采购',
  party: '',
  amount: undefined as number | undefined,
  signDate: '',
  startDate: '',
  endDate: '',
  discountRate: undefined as number | undefined,
  purchaseOption: false,
  paymentPlanText: '',
  status: '生效',
  remark: '',
})

const rules = computed<FormRules>(() => ({
  contractNo: [{ required: !editingNo.value, message: '请输入合同编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入合同名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择合同类型', trigger: 'change' }],
}))

function openCreate() {
  if (!canWrite.value) return
  editingNo.value = ''
  Object.assign(form, {
    contractNo: '',
    name: '',
    type: '采购',
    party: '',
    amount: undefined,
    signDate: '',
    startDate: '',
    endDate: '',
    discountRate: undefined,
    purchaseOption: false,
    paymentPlanText: '',
    status: '生效',
    remark: '',
  })
  dialogVisible.value = true
}

function openEdit(row: ContractItem) {
  if (!canWrite.value) return
  editingNo.value = row.contractNo
  Object.assign(form, {
    contractNo: row.contractNo || '',
    name: row.name || '',
    type: row.type || '采购',
    party: row.party || '',
    amount: row.amount ?? undefined,
    signDate: row.signDate || '',
    startDate: row.startDate || '',
    endDate: row.endDate || '',
    discountRate: row.discountRate ?? undefined,
    purchaseOption: !!row.purchaseOption,
    paymentPlanText: row.paymentPlan?.length ? JSON.stringify(row.paymentPlan, null, 2) : '',
    status: row.status || '生效',
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return

  let paymentPlan: Array<Record<string, unknown>> | undefined
  if (form.paymentPlanText.trim()) {
    try {
      const parsed: unknown = JSON.parse(form.paymentPlanText)
      if (!Array.isArray(parsed)) {
        throw new Error('not array')
      }
      paymentPlan = parsed as Array<Record<string, unknown>>
    } catch {
      ElMessage.error('租金计划 paymentPlan 必须是 JSON 数组，如 [{"period":"2026-01","amount":1000}]')
      return
    }
  }

  const payload = {
    name: form.name.trim(),
    type: form.type,
    party: form.party || undefined,
    amount: form.amount,
    signDate: form.signDate || undefined,
    startDate: form.startDate || undefined,
    endDate: form.endDate || undefined,
    discountRate: form.discountRate,
    purchaseOption: form.purchaseOption,
    paymentPlan,
    status: form.status || undefined,
    remark: form.remark || undefined,
  }

  saving.value = true
  try {
    if (editingNo.value) {
      // 编辑时日期空串要显式发出以便清空
      await updateContract(editingNo.value, {
        ...payload,
        signDate: form.signDate || '',
        startDate: form.startDate || '',
        endDate: form.endDate || '',
      })
      ElMessage.success('保存成功')
    } else {
      await createContract({ contractNo: form.contractNo.trim(), ...payload })
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

async function remove(row: ContractItem) {
  if (!canWrite.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除合同「${row.contractNo} ${row.name}」吗？该操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteContract(row.contractNo)
    ElMessage.success('删除成功')
    void load()
  } catch {
    // 拦截器已提示（被资产引用 409 等）
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">合同管理</div>

    <div class="page-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="合同编号 / 名称 / 当事人"
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
        <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <div style="flex: 1"></div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新增合同</el-button>
    </div>

    <el-table v-loading="listLoading" :data="records" border stripe row-key="contractNo">
      <el-table-column prop="contractNo" label="合同编号" min-width="140" fixed="left" />
      <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column prop="party" label="当事人" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.party || '-' }}</template>
      </el-table-column>
      <el-table-column label="金额" width="130" align="right">
        <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="signDate" label="签署日" width="105">
        <template #default="{ row }">{{ row.signDate || '-' }}</template>
      </el-table-column>
      <el-table-column prop="startDate" label="开始" width="105">
        <template #default="{ row }">{{ row.startDate || '-' }}</template>
      </el-table-column>
      <el-table-column prop="endDate" label="结束" width="105">
        <template #default="{ row }">{{ row.endDate || '-' }}</template>
      </el-table-column>
      <el-table-column label="折现率" width="100" align="right">
        <template #default="{ row }">{{ fmtRate(row.discountRate) }}</template>
      </el-table-column>
      <el-table-column label="留购权" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.purchaseOption" type="success" size="small">是</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ row.status || '-' }}</el-tag>
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
      :title="editingNo ? '编辑合同' : '新增合同'"
      width="760px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="合同编号" prop="contractNo">
              <el-input v-model="form.contractNo" :disabled="!!editingNo" placeholder="创建后不可修改" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="type">
              <el-select v-model="form.type" style="width: 100%">
                <el-option v-for="t in TYPE_OPTIONS" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="当事人">
              <el-input v-model="form.party" placeholder="供应商 / 出租方 / 客户" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="金额">
              <el-input-number
                v-model="form.amount"
                :min="0"
                :precision="2"
                :controls="false"
                placeholder="合同金额"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="签署日期">
              <el-date-picker
                v-model="form.signDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开始日期">
              <el-date-picker
                v-model="form.startDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="结束日期">
              <el-date-picker
                v-model="form.endDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="折现率">
              <el-input-number
                v-model="form.discountRate"
                :min="0"
                :max="1"
                :precision="6"
                :step="0.0001"
                placeholder="如 0.045"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="留购权">
              <el-switch v-model="form.purchaseOption" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="租金计划">
              <el-input
                v-model="form.paymentPlanText"
                type="textarea"
                :rows="4"
                placeholder='JSON 数组，如 [{"period":"2026-01","amount":1000}]；留空表示无'
              />
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
