<script setup lang="ts">
/**
 * 服务台（自助门户）：按目录挑选服务 → 填写标准表单 → 自动生成工单；「我的请求」跟踪进度。
 * 管理员可在「目录维护」中增删服务、配置表单字段与可见角色。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import AssetPicker from '@/components/AssetPicker.vue'
import {
  createCatalogItem,
  deleteCatalogItem,
  fetchCatalog,
  fetchCatalogWoTypes,
  fetchMyRequests,
  submitCatalogRequest,
  updateCatalogItem,
} from '@/api/itsm'
import type { CatalogItem, CatalogPayload, FormField } from '@/api/itsm'

const auth = useAuthStore()
const router = useRouter()
const isAdmin = computed(() => !!auth.user?.isAdmin)
const tab = ref('catalog')

const items = ref<CatalogItem[]>([])
const keyword = ref('')
const grouped = computed(() => {
  const kw = keyword.value.trim()
  const m = new Map<string, CatalogItem[]>()
  for (const i of items.value) {
    if (kw && !i.name.includes(kw) && !(i.description || '').includes(kw)) continue
    if (!m.has(i.category)) m.set(i.category, [])
    m.get(i.category)!.push(i)
  }
  return [...m.entries()]
})

async function loadItems() {
  try {
    items.value = await fetchCatalog()
  } catch {
    // ignore
  }
}

/* ---------------- 提交 ---------------- */
const dialog = ref(false)
const current = ref<CatalogItem | null>(null)
const values = reactive<Record<string, any>>({})
const submitting = ref(false)
function openItem(i: CatalogItem) {
  current.value = i
  for (const k of Object.keys(values)) delete values[k]
  for (const f of i.formSchema) values[f.key] = f.type === 'assets' ? [] : f.type === 'number' ? undefined : ''
  dialog.value = true
}
async function submit() {
  const i = current.value!
  for (const f of i.formSchema) {
    const v = values[f.key]
    if (f.required && (v == null || v === '' || (Array.isArray(v) && !v.length))) {
      ElMessage.warning(`请填写「${f.label}」`)
      return
    }
  }
  submitting.value = true
  try {
    const r = await submitCatalogRequest(i.id, { ...values })
    dialog.value = false
    await ElMessageBox.alert(`已提交，工单号 ${r.woNo}。可在「我的请求」中跟踪处理进度。`, '提交成功', { type: 'success' })
    tab.value = 'mine'
    void loadMine()
  } catch {
    // ignore
  } finally {
    submitting.value = false
  }
}

/* ---------------- 我的请求 ---------------- */
const mine = ref<any[]>([])
const mineTotal = ref(0)
const minePage = ref(1)
async function loadMine() {
  try {
    const r = await fetchMyRequests(minePage.value, 20)
    mine.value = r.records
    mineTotal.value = r.total
  } catch {
    // ignore
  }
}
const STATUS_TYPE: Record<string, 'info' | 'warning' | 'primary' | 'success' | 'danger'> = {
  待审批: 'warning',
  待处理: 'primary',
  处理中: 'primary',
  待验收: 'warning',
  已完成: 'success',
  已关闭: 'info',
  已驳回: 'danger',
}

/* ---------------- 目录维护 ---------------- */
const allItems = ref<CatalogItem[]>([])
const woTypes = ref<string[]>([])
async function loadAdmin() {
  try {
    ;[allItems.value, woTypes.value] = await Promise.all([fetchCatalog(true), fetchCatalogWoTypes()])
  } catch {
    // ignore
  }
}
const eDialog = ref(false)
const editingId = ref<number | null>(null)
const eForm = reactive<CatalogPayload>({
  code: '',
  name: '',
  category: '通用服务',
  description: '',
  icon: 'Service',
  woType: '故障维修',
  priority: '中',
  formSchema: [],
  visibleRoles: [],
  slaHint: '',
  enabled: true,
  sort: 100,
})
const FIELD_TYPES: Record<string, string> = {
  text: '单行文本',
  textarea: '多行文本',
  number: '数字',
  select: '下拉选择',
  date: '日期',
  asset: '单台设备',
  assets: '多台设备',
  rack: '机柜',
}
function openEdit(i?: CatalogItem) {
  editingId.value = i?.id ?? null
  Object.assign(eForm, i
    ? { ...i, formSchema: i.formSchema.map((f) => ({ ...f, options: f.options ? [...f.options] : undefined })), visibleRoles: [...i.visibleRoles] }
    : { code: '', name: '', category: '通用服务', description: '', icon: 'Service', woType: woTypes.value[0] || '故障维修', priority: '中', formSchema: [{ key: 'detail', label: '需求描述', type: 'textarea', required: true }], visibleRoles: [], slaHint: '', enabled: true, sort: 100 })
  eDialog.value = true
}
function addField() {
  eForm.formSchema.push({ key: `field${eForm.formSchema.length + 1}`, label: '', type: 'text', required: false })
}
function moveField(i: number, d: number) {
  const a = eForm.formSchema
  const j = i + d
  if (j < 0 || j >= a.length) return
  ;[a[i], a[j]] = [a[j], a[i]]
}
async function saveItem() {
  try {
    if (editingId.value) await updateCatalogItem(editingId.value, { ...eForm })
    else await createCatalogItem({ ...eForm })
    ElMessage.success('已保存')
    eDialog.value = false
    void loadAdmin()
    void loadItems()
  } catch {
    // ignore
  }
}
async function removeItem(i: CatalogItem) {
  try {
    await ElMessageBox.confirm(`删除服务「${i.name}」？已有请求记录的服务将改为停用`, '删除', { type: 'warning' })
    await deleteCatalogItem(i.id)
    void loadAdmin()
    void loadItems()
  } catch {
    // 取消
  }
}
function onTab(n: string | number) {
  if (n === 'mine') void loadMine()
  if (n === 'admin') void loadAdmin()
}

function fmt(v?: string) {
  return v ? v.replace('T', ' ').slice(0, 16) : '-'
}

onMounted(loadItems)
</script>

<template>
  <div class="page-card">
    <div class="page-title">服务台</div>
    <el-tabs v-model="tab" @tab-change="onTab">
      <el-tab-pane label="服务目录" name="catalog">
        <div class="page-toolbar">
          <el-input v-model="keyword" placeholder="搜索服务" clearable style="width: 260px">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
        <el-empty v-if="!grouped.length" description="暂无可用服务" />
        <div v-for="[cat, list] in grouped" :key="cat" class="group">
          <h4 class="sec">{{ cat }}</h4>
          <div class="grid">
            <div v-for="i in list" :key="i.id" class="svc" @click="openItem(i)">
              <div class="icon"><el-icon :size="26"><component :is="i.icon || 'Service'" /></el-icon></div>
              <div class="body">
                <div class="name">{{ i.name }}</div>
                <div class="desc">{{ i.description }}</div>
                <div class="hint">
                  <span>{{ i.woType }}</span>
                  <span v-if="i.slaHint">· {{ i.slaHint }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的请求" name="mine">
        <el-table :data="mine" border stripe size="small">
          <el-table-column label="工单" width="170">
            <template #default="{ row }"><el-link type="primary" @click="router.push(`/workorders/${row.woNo}`)">{{ row.woNo }}</el-link></template>
          </el-table-column>
          <el-table-column prop="itemName" label="服务" width="140" />
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag size="small" :type="STATUS_TYPE[row.status] || 'info'">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="handler" label="处理人" width="100" />
          <el-table-column label="提交时间" width="140"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
          <el-table-column label="承诺解决" width="140"><template #default="{ row }">{{ fmt(row.slaDeadline) }}</template></el-table-column>
        </el-table>
        <div class="page-pagination">
          <el-pagination v-model:current-page="minePage" :page-size="20" :total="mineTotal" layout="total, prev, pager, next" @current-change="loadMine" />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="isAdmin" label="目录维护" name="admin">
        <div class="page-toolbar">
          <div style="flex: 1"></div>
          <el-button type="primary" :icon="'Plus'" @click="openEdit()">新增服务</el-button>
        </div>
        <el-table :data="allItems" border size="small">
          <el-table-column prop="sort" label="排序" width="60" />
          <el-table-column label="服务" min-width="200">
            <template #default="{ row }">{{ row.name }} <span class="sub">{{ row.code }}</span></template>
          </el-table-column>
          <el-table-column prop="category" label="分组" width="110" />
          <el-table-column prop="woType" label="生成工单" width="100" />
          <el-table-column prop="priority" label="优先级" width="70" />
          <el-table-column label="字段" width="60"><template #default="{ row }">{{ row.formSchema.length }}</template></el-table-column>
          <el-table-column label="可见" width="140">
            <template #default="{ row }">{{ row.visibleRoles.length ? row.visibleRoles.join('、') : '所有人' }}</template>
          </el-table-column>
          <el-table-column label="启用" width="70">
            <template #default="{ row }"><el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="removeItem(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialog" :title="current?.name" width="620px">
      <template v-if="current">
        <div class="sub" style="margin-bottom: 12px">{{ current.description }}</div>
        <el-form label-width="100px">
          <el-form-item v-for="f in current.formSchema" :key="f.key" :label="f.label" :required="f.required">
            <el-input v-if="f.type === 'text' || f.type === 'rack'" v-model="values[f.key]" :placeholder="f.placeholder || (f.type === 'rack' ? '机柜编码' : '')" :maxlength="f.maxLength" />
            <el-input v-else-if="f.type === 'textarea'" v-model="values[f.key]" type="textarea" :rows="4" :placeholder="f.placeholder" :maxlength="f.maxLength || 4000" show-word-limit />
            <el-input-number v-else-if="f.type === 'number'" v-model="values[f.key]" :controls="false" style="width: 200px" />
            <el-select v-else-if="f.type === 'select'" v-model="values[f.key]" style="width: 100%">
              <el-option v-for="o in f.options" :key="o" :label="o" :value="o" />
            </el-select>
            <el-date-picker v-else-if="f.type === 'date'" v-model="values[f.key]" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
            <AssetPicker v-else-if="f.type === 'asset'" v-model="values[f.key]" style="width: 100%" />
            <AssetPicker v-else-if="f.type === 'assets'" v-model="values[f.key]" multiple style="width: 100%" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <span class="sub" style="float: left">提交后生成「{{ current?.woType }}」工单，按既有审批与 SLA 流程处理</span>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="eDialog" :title="editingId ? '编辑服务' : '新增服务'" width="900px" top="4vh">
      <el-form label-width="80px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="编码" required><el-input v-model="eForm.code" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="名称" required><el-input v-model="eForm.name" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="分组"><el-input v-model="eForm.category" /></el-form-item></el-col>
          <el-col :span="8">
            <el-form-item label="生成工单">
              <el-select v-model="eForm.woType" style="width: 100%">
                <el-option v-for="t in woTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优先级">
              <el-radio-group v-model="eForm.priority">
                <el-radio-button value="高">高</el-radio-button>
                <el-radio-button value="中">中</el-radio-button>
                <el-radio-button value="低">低</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="图标"><el-input v-model="eForm.icon" placeholder="Element Plus 图标名" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="说明"><el-input v-model="eForm.description" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="10">
            <el-form-item label="可见角色">
              <el-select v-model="eForm.visibleRoles" multiple filterable allow-create placeholder="留空 = 所有人" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6"><el-form-item label="时效提示"><el-input v-model="eForm.slaHint" /></el-form-item></el-col>
          <el-col :span="4"><el-form-item label="排序"><el-input-number v-model="eForm.sort" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="4"><el-form-item label="启用"><el-switch v-model="eForm.enabled" /></el-form-item></el-col>
        </el-row>
        <h4 class="sec">表单字段 <el-button size="small" @click="addField">添加字段</el-button></h4>
        <el-table :data="eForm.formSchema" size="small" border>
          <el-table-column label="key" width="120"><template #default="{ row }"><el-input v-model="row.key" size="small" /></template></el-table-column>
          <el-table-column label="标签" width="140"><template #default="{ row }"><el-input v-model="row.label" size="small" /></template></el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-select v-model="row.type" size="small">
                <el-option v-for="(l, k) in FIELD_TYPES" :key="k" :label="l" :value="k" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="必填" width="60" align="center"><template #default="{ row }"><el-checkbox v-model="row.required" /></template></el-table-column>
          <el-table-column label="选项（下拉）">
            <template #default="{ row }">
              <el-select v-if="row.type === 'select'" v-model="row.options" multiple filterable allow-create default-first-option size="small" style="width: 100%" />
              <span v-else class="sub">{{ row.type === 'assets' || row.type === 'asset' ? '→ 工单关联资产' : row.type === 'rack' ? '→ 目标机柜' : row.key === 'expectFinish' ? '→ 期望完成日期' : '→ 写入工单描述' }}</span>
            </template>
          </el-table-column>
          <el-table-column width="110">
            <template #default="{ $index }">
              <el-button link size="small" @click="moveField($index, -1)">↑</el-button>
              <el-button link size="small" @click="moveField($index, 1)">↓</el-button>
              <el-button link type="danger" size="small" @click="eForm.formSchema.splice($index, 1)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="eDialog = false">取消</el-button>
        <el-button type="primary" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sec {
  margin: 16px 0 10px;
  font-size: 14px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}
.svc {
  display: flex;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.svc:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 2px 10px rgba(64, 158, 255, 0.12);
}
.icon {
  flex: 0 0 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.name {
  font-weight: 600;
}
.desc {
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin: 4px 0;
  line-height: 1.5;
}
.hint,
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
