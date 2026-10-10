<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import http from '@/api/client'
import type { ModuleInfo, PageResult, RoleItem } from '@/api/types'

/* ---------------- 角色列表 ---------------- */
const query = reactive({ page: 1, size: 20, keyword: '' })
const total = ref(0)
const records = ref<RoleItem[]>([])
const listLoading = ref(false)

async function load() {
  listLoading.value = true
  try {
    const res = await http.get<any, PageResult<RoleItem>>('/roles', {
      params: { page: query.page, size: query.size, keyword: query.keyword.trim() || undefined },
    })
    total.value = res.total ?? 0
    records.value = res.records ?? []
  } catch {
    // 拦截器已提示
  } finally {
    listLoading.value = false
  }
}

/* ---------------- 模块清单（权限矩阵行） ---------------- */
const modules = ref<ModuleInfo[]>([])

async function loadModules() {
  try {
    modules.value = await http.get<any, ModuleInfo[]>('/modules')
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- v3.4：字段级权限（目前支持资产、工单） ---------------- */
interface FieldDef {
  field: string
  label: string
}
const fieldCatalog = ref<Record<string, FieldDef[]>>({})
const FIELD_MODULE_NAMES: Record<string, string> = { assets: '资产', workorders: '工单' }
/** 每个模块：隐藏字段（不可见、不可写）与只读字段（可见、不可写） */
const fieldRules = reactive<Record<string, { hidden: string[]; readonly: string[] }>>({})

async function loadFieldCatalog() {
  try {
    fieldCatalog.value = await http.get<any, Record<string, FieldDef[]>>('/roles/field-catalog')
  } catch {
    // 拦截器已提示
  }
}

function buildFieldRules(role?: RoleItem) {
  for (const m of Object.keys(fieldCatalog.value)) {
    const cfg = (role?.fieldPerms as Record<string, { hidden?: string[]; readonly?: string[] }> | undefined)?.[m]
    fieldRules[m] = { hidden: [...(cfg?.hidden ?? [])], readonly: [...(cfg?.readonly ?? [])] }
  }
}

function moduleReadable(code: string): boolean {
  return !!matrixRows.value.find((r) => r.code === code)?.read
}

/* ---------------- 新增 / 编辑（权限矩阵） ---------------- */
interface MatrixRow {
  code: string
  name: string
  group: string
  read: boolean
  write: boolean
  del: boolean
  exp: boolean
  /** 数据范围（仅工单 / 资产生效）：ALL 全部 / DEPT 本部门 / SELF 本人 */
  scope: 'ALL' | 'DEPT' | 'SELF'
}

/** v3.3 起后端真正执行数据范围的模块 */
const SCOPED_MODULES = new Set(['workorders', 'assets'])
const SCOPE_OPTIONS = [
  { value: 'ALL', label: '全部' },
  { value: 'DEPT', label: '本部门' },
  { value: 'SELF', label: '仅本人' },
]
const SCOPE_TIPS: Record<string, string> = {
  workorders: '本人 = 我申请 / 我处理 / 抄送我 / 待我审批；本部门 = 本部门同事申请的工单 + 本人',
  assets: '本人 = 责任人是我；本部门 = 使用部门为本部门 + 本人',
}

const dialogVisible = ref(false)
const editingCode = ref('')
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ roleCode: '', name: '', description: '' })
const matrixRows = ref<MatrixRow[]>([])

const rules: FormRules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

function buildRows(role?: RoleItem) {
  matrixRows.value = modules.value.map((m) => {
    const mp = role?.modulePerms?.[m.code]
    const ap = role?.actionPerms?.[m.code]
    const ds = (role?.dataScopes as Record<string, { type?: string }> | undefined)?.[m.code]?.type
    return {
      code: m.code,
      name: m.name,
      group: m.group,
      read: !!mp?.read,
      write: !!mp?.write,
      del: !!ap?.delete,
      exp: !!ap?.export,
      scope: (ds === 'DEPT' || ds === 'SELF' ? ds : 'ALL') as MatrixRow['scope'],
    }
  })
}

function openCreate() {
  editingCode.value = ''
  Object.assign(form, { roleCode: '', name: '', description: '' })
  buildRows()
  buildFieldRules()
  dialogVisible.value = true
}

function openEdit(row: RoleItem) {
  editingCode.value = row.roleCode
  Object.assign(form, {
    roleCode: row.roleCode,
    name: row.name,
    description: row.description || '',
  })
  buildRows(row)
  buildFieldRules(row)
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    // 读/写 -> modulePerms；删除/导出 -> actionPerms
    // dataScopes 结构必须是 { 模块码: { type: "ALL"|"DEPT"|"SELF" } }，不能发 { type: "ALL" }
    const modulePerms: Record<string, { read: boolean; write: boolean }> = {}
    const actionPerms: Record<string, { delete: boolean; export: boolean }> = {}
    const dataScopes: Record<string, { type: string }> = {}
    for (const r of matrixRows.value) {
      modulePerms[r.code] = { read: r.read, write: r.read ? r.write : false }
      actionPerms[r.code] = { delete: r.del, export: r.exp }
      if (r.read) {
        dataScopes[r.code] = { type: SCOPED_MODULES.has(r.code) ? r.scope : 'ALL' }
      }
    }
    // 字段权限：同一字段既隐藏又只读时按隐藏处理；没有访问权的模块不提交
    const fieldPerms: Record<string, { hidden: string[]; readonly: string[] }> = {}
    for (const [m, r] of Object.entries(fieldRules)) {
      if (!moduleReadable(m)) continue
      const hidden = [...new Set(r.hidden)]
      const readonly = [...new Set(r.readonly)].filter((f) => !hidden.includes(f))
      if (hidden.length || readonly.length) fieldPerms[m] = { hidden, readonly }
    }
    const payload = {
      roleCode: form.roleCode.trim(),
      name: form.name.trim(),
      description: form.description,
      modulePerms,
      dataScopes,
      fieldPerms,
      actionPerms,
    }
    if (editingCode.value) {
      await http.put(`/roles/${encodeURIComponent(editingCode.value)}`, payload)
      ElMessage.success('保存成功')
    } else {
      await http.post('/roles', payload)
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

async function remove(row: RoleItem) {
  if (row.builtIn) return
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await http.delete(`/roles/${encodeURIComponent(row.roleCode)}`)
    ElMessage.success('删除成功')
    void load()
  } catch {
    // 拦截器已提示
  }
}

onMounted(() => {
  void load()
  void loadModules()
  void loadFieldCatalog()
})
</script>

<template>
  <div class="page-card">
    <div class="page-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索角色编码 / 名称"
        clearable
        style="width: 240px"
        @keyup.enter="() => { query.page = 1; load() }"
        @clear="() => { query.page = 1; load() }"
      />
      <div style="flex: 1"></div>
      <el-button type="primary" @click="openCreate">新增角色</el-button>
    </div>

    <el-table v-loading="listLoading" :data="records" border stripe>
      <el-table-column prop="roleCode" label="角色编码" min-width="120" />
      <el-table-column prop="name" label="角色名称" min-width="130" />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column label="内置" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.builtIn" type="warning" size="small">内置</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="160" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" :disabled="!!row.builtIn" @click="remove(row)">
            删除
          </el-button>
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

    <!-- 新增 / 编辑角色（权限矩阵） -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingCode ? '编辑角色' : '新增角色'"
      width="880px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input
            v-model="form.roleCode"
            :disabled="!!editingCode"
            placeholder="如 warehouse-admin，创建后不可修改"
          />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="权限配置">
          <el-table :data="matrixRows" border max-height="360" style="width: 100%" size="small">
            <el-table-column label="模块" min-width="200">
              <template #default="{ row }">
                <div>{{ row.group }} / {{ row.name }}</div>
                <div class="module-code">{{ row.code }}</div>
              </template>
            </el-table-column>
            <el-table-column label="读" width="70" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.read" />
              </template>
            </el-table-column>
            <el-table-column label="写" width="70" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.write" />
              </template>
            </el-table-column>
            <el-table-column width="70" align="center">
              <template #header>
                <el-tooltip content="删除需显式授予：有写权限且勾选删除才可删除（v3.4 起生效）" placement="top">
                  <span>删除</span>
                </el-tooltip>
              </template>
              <template #default="{ row }">
                <el-checkbox v-model="row.del" />
              </template>
            </el-table-column>
            <el-table-column label="导出" width="70" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.exp" />
              </template>
            </el-table-column>
            <el-table-column label="数据范围" width="130" align="center">
              <template #default="{ row }">
                <el-tooltip v-if="SCOPED_MODULES.has(row.code)" :content="SCOPE_TIPS[row.code]" placement="top">
                  <el-select v-model="row.scope" size="small" :disabled="!row.read" style="width: 100px">
                    <el-option v-for="o in SCOPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
                  </el-select>
                </el-tooltip>
                <span v-else class="module-code">全部</span>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
        <el-form-item label="字段权限">
          <div class="field-rules">
            <div v-for="(defs, m) in fieldCatalog" :key="m" class="field-rule-row">
              <div class="field-rule-name">
                {{ FIELD_MODULE_NAMES[m] || m }}
                <span v-if="!moduleReadable(String(m))" class="module-code">（未授予读权限）</span>
              </div>
              <template v-if="fieldRules[m]">
                <el-select
                  v-model="fieldRules[m].hidden"
                  multiple
                  collapse-tags
                  collapse-tags-tooltip
                  clearable
                  :disabled="!moduleReadable(String(m))"
                  placeholder="隐藏字段（不可见、不可改）"
                  style="width: 300px"
                >
                  <el-option v-for="d in defs" :key="d.field" :label="d.label" :value="d.field" />
                </el-select>
                <el-select
                  v-model="fieldRules[m].readonly"
                  multiple
                  collapse-tags
                  collapse-tags-tooltip
                  clearable
                  :disabled="!moduleReadable(String(m))"
                  placeholder="只读字段（可见、不可改）"
                  style="width: 300px"
                >
                  <el-option
                    v-for="d in defs"
                    :key="d.field"
                    :label="d.label"
                    :value="d.field"
                    :disabled="fieldRules[m].hidden.includes(d.field)"
                  />
                </el-select>
              </template>
            </div>
            <div class="module-code">
              多个角色叠加时，字段在任一角色可见即可见、任一可写角色未限制即可改；管理员不受字段规则约束。
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.field-rules {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.field-rule-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.field-rule-name {
  width: 120px;
  color: var(--el-text-color-regular);
}
.module-code {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
