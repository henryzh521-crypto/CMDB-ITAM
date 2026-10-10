<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createDict, deleteDict, fetchDicts, updateDict } from '@/api/dict'
import type { DictItem } from '@/api/dict'

/* ---------------- 类型分组 ---------------- */
/** 常用类型展示名与固定排序；未知类型追加在后（键即原样展示） */
const TYPE_LABELS: Record<string, string> = {
  asset_status_biz: '资产业务状态',
  asset_status_fin: '资产财务状态',
  region: '区域',
  material_cat: '物料分类',
  unit: '计量单位',
  warehouse_type: '库房类型',
  code_rule: '资产编码规则',
}
const TYPE_ORDER = [
  'asset_status_biz',
  'asset_status_fin',
  'region',
  'material_cat',
  'unit',
  'warehouse_type',
  'code_rule',
]
/** 状态类字典：额外提示状态机约束 */
const STATUS_TYPES = ['asset_status_biz', 'asset_status_fin']
/** 编码规则：发号器读取的资产编号模板，额外提示占位符与生效范围 */
const RULE_TYPES = ['code_rule']
/** 编码规则的占位符说明（与后端 AssetCodeRule 保持一致） */
const RULE_HINT =
  '资产编号模板：{CATEGORY} 类别码 / {REGION} 区域码 / {YYYY} 年 / {YY} 年后两位 / ' +
  '{MM} 月 / {DD} 日 / {SEQ:n} 流水号（零填充 n 位，须以它收尾）。' +
  '修改后仅对新建资产生效、存量编号不变；该规则被发号器直接读取，不可停用或删除。'

const all = ref<DictItem[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    all.value = await fetchDicts()
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
  // 当前 tab 类型被删空/清空后回落到首个类型
  if (types.value.length > 0 && !groups.value.has(activeType.value)) {
    activeType.value = types.value[0]
  }
}

/** 类型 → 行（保序：TYPE_ORDER 在前，未知类型按首次出现追加） */
const groups = computed(() => {
  const map = new Map<string, DictItem[]>()
  for (const t of TYPE_ORDER) {
    map.set(t, [])
  }
  for (const row of all.value) {
    if (!map.has(row.dictType)) map.set(row.dictType, [])
    map.get(row.dictType)!.push(row)
  }
  for (const [t, rows] of map) {
    if (rows.length === 0) map.delete(t)
  }
  // 空类型（被清空后）仍保留当前 tab；未建类型的 tab 不出现，由新建对话框创建
  return map
})

const types = computed(() => [...groups.value.keys()])
const activeType = ref(TYPE_ORDER[0])

const activeRows = computed(() => groups.value.get(activeType.value) ?? [])
const activeLabel = computed(() => typeLabel(activeType.value))
const isStatusType = computed(() => STATUS_TYPES.includes(activeType.value))
const isRuleType = computed(() => RULE_TYPES.includes(activeType.value))

function typeLabel(t: string): string {
  return TYPE_LABELS[t] ?? t
}

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ dictType: '', dictKey: '', dictValue: '', sort: 0 })

const rules: FormRules = {
  dictType: [{ required: true, message: '请输入字典类型', trigger: 'blur' }],
  dictKey: [{ required: true, message: '请输入字典键', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典值/名称', trigger: 'blur' }],
}

/** 创建对话框的类型下拉：已知类型 + 当前 tab，允许自定义新类型 */
const typeOptions = computed(() => {
  const set = new Set([...TYPE_ORDER, ...types.value])
  if (form.dictType && !set.has(form.dictType)) set.add(form.dictType)
  return [...set]
})

/** 当前编辑的是不是编码规则（影响键/值的输入提示） */
const isRuleForm = computed(() => RULE_TYPES.includes(form.dictType))

function openCreate() {
  editingId.value = null
  const maxSort = activeRows.value.reduce((m, r) => Math.max(m, r.sort ?? 0), 0)
  Object.assign(form, {
    dictType: activeType.value,
    // 编码规则全局仅 asset 一条，预填避免误建其它键（后端也会 400 拦截）
    dictKey: activeType.value === 'code_rule' ? 'asset' : '',
    dictValue: '',
    sort: maxSort + 1,
  })
  dialogVisible.value = true
}

function openEdit(row: DictItem) {
  editingId.value = row.id
  Object.assign(form, {
    dictType: row.dictType,
    dictKey: row.dictKey,
    dictValue: row.dictValue,
    sort: row.sort ?? 0,
  })
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value != null) {
      const row = all.value.find((r) => r.id === editingId.value)
      await updateDict(editingId.value, {
        dictType: form.dictType,
        dictKey: form.dictKey,
        dictValue: form.dictValue,
        sort: form.sort,
        status: row?.status ?? 1,
      })
      ElMessage.success('保存成功')
    } else {
      await createDict({
        dictType: form.dictType.trim(),
        dictKey: form.dictKey.trim(),
        dictValue: form.dictValue.trim(),
        sort: form.sort,
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    void load()
  } catch {
    // 拦截器已提示（重复键 409 / 无权限 403 等）
  } finally {
    saving.value = false
  }
}

/* ---------------- 启用 / 停用 / 删除 ---------------- */
async function toggleStatus(row: DictItem) {
  const next = row.status === 1 ? 0 : 1
  const verb = next === 0 ? '停用' : '启用'
  try {
    await updateDict(row.id, {
      dictType: row.dictType,
      dictKey: row.dictKey,
      dictValue: row.dictValue,
      sort: row.sort,
      status: next,
    })
    ElMessage.success(`已${verb}`)
    void load()
  } catch {
    // 拦截器已提示（被引用停用 409 等）
  }
}

async function remove(row: DictItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除字典项「${row.dictKey}」吗？若已被业务数据引用将被拒绝。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteDict(row.id)
    ElMessage.success('删除成功')
    void load()
  } catch {
    // 拦截器已提示（被引用/系统状态 409 等）
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page-card">
    <div class="page-toolbar">
        <span class="toolbar-hint">区域 / 分类 / 单位 / 库房类型 / 资产状态 / 根因分类等下拉项，以及资产编号规则统一在此维护</span>
      <div style="flex: 1"></div>
      <el-button :loading="loading" @click="load">刷新</el-button>
      <el-button type="primary" @click="openCreate">新增字典项</el-button>
    </div>

    <el-tabs v-model="activeType">
      <el-tab-pane v-for="t in types" :key="t" :label="typeLabel(t)" :name="t">
        <el-alert
          v-if="STATUS_TYPES.includes(t)"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 10px"
          :title="`「${typeLabel(t)}」由状态机迁移矩阵管理流转：新增状态仅进入资产编辑下拉，需状态机支持方能用于状态流转；被业务数据引用的项不可停用/删除，系统状态不可删除（可停用）。`"
        />
        <el-alert
          v-else-if="RULE_TYPES.includes(t)"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 10px"
          title="资产编号生成规则"
        >
          {{ RULE_HINT }}
        </el-alert>
        <el-table v-loading="loading" :data="groups.get(t) ?? []" border stripe>
          <el-table-column
            prop="dictKey"
            :label="isRuleType ? '规则键' : '字典键（存库值）'"
            min-width="140"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span>{{ row.dictKey }}</span>
              <el-tag v-if="row.systemManaged" size="small" type="info" style="margin-left: 6px">
                系统状态
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="dictValue"
            :label="isRuleType ? '规则模板' : '字典值 / 名称（展示）'"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column prop="sort" label="排序" width="80" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button
                v-if="row.status === 1 && !RULE_TYPES.includes(t)"
                link
                type="warning"
                @click="toggleStatus(row)"
              >
                停用
              </el-button>
              <el-button
                v-else-if="!RULE_TYPES.includes(t)"
                link
                type="success"
                @click="toggleStatus(row)"
                >启用</el-button
              >
              <el-tooltip
                v-if="row.systemManaged"
                content="状态字典项由状态机迁移矩阵管理，不可删除（可停用）"
                placement="top"
              >
                <span><el-button link type="danger" disabled>删除</el-button></span>
              </el-tooltip>
              <el-button
                v-else-if="!RULE_TYPES.includes(t)"
                link
                type="danger"
                @click="remove(row)"
                >删除</el-button
              >
              <el-tooltip v-else content="编码规则由资产编号生成读取，不可停用或删除" placement="top">
                <span><el-button link type="danger" disabled>删除</el-button></span>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane v-if="types.length === 0 && !loading" name="__empty" label="暂无字典" disabled />
    </el-tabs>

    <!-- 新增 / 编辑字典项 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId != null ? '编辑字典项' : '新增字典项'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="字典类型" prop="dictType">
          <el-select
            v-model="form.dictType"
            filterable
            allow-create
            default-first-option
            :disabled="editingId != null"
            placeholder="选择或输入新类型"
            style="width: 100%"
          >
            <el-option v-for="t in typeOptions" :key="t" :label="typeLabel(t)" :value="t" />
          </el-select>
          <div v-if="editingId != null" class="form-hint">类型创建后不可修改（键被业务数据引用）</div>
        </el-form-item>
        <el-form-item label="字典键" prop="dictKey">
          <el-input
            v-model="form.dictKey"
            :disabled="editingId != null"
            :placeholder="
              isRuleForm
                ? '固定为 asset（创建后不可修改）'
                : '存库值，如区域编码 QY（创建后不可修改）'
            "
          />
          <div v-if="isRuleForm" class="form-hint">编码规则全局仅一条，键固定为 asset</div>
        </el-form-item>
        <el-form-item :label="isRuleForm ? '规则模板' : '字典值 / 名称'" prop="dictValue">
          <el-input
            v-model="form.dictValue"
            :placeholder="
              isRuleForm
                ? '如 {CATEGORY}-{REGION}{YY}-{YYYY}-{SEQ:5}'
                : '下拉展示文案，如 庆阳'
            "
          />
          <div v-if="isRuleForm" class="form-hint">
            占位符：{CATEGORY} {REGION} {YYYY} {YY} {MM} {DD} {SEQ:n}；须以 {SEQ:n} 收尾，仅影响新建资产
          </div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
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
.toolbar-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.form-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  margin-top: 2px;
}
</style>
