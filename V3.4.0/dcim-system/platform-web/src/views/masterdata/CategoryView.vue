<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import {
  createCategory,
  deleteCategory,
  fetchCategoryImportTemplate,
  fetchCategoryTree,
  importCategories,
  updateCategory,
} from '@/api/masterdata'
import type { CategoryNode } from '@/api/masterdata'
import type { CsvImportResult } from '@/api/types'
import { saveBlob } from '@/api/asset'
import { checkCsvImportSize } from '@/utils/upload'

/* ---------------- 分类树 ---------------- */
const tree = ref<CategoryNode[]>([])
const treeLoading = ref(false)

async function loadTree() {
  treeLoading.value = true
  try {
    tree.value = (await fetchCategoryTree()) || []
  } catch {
    // 拦截器已提示
  } finally {
    treeLoading.value = false
  }
}

function isDisabled(status?: string | number): boolean {
  const s = String(status ?? '').toUpperCase()
  return s === '0' || s === 'DISABLED'
}

function statusText(status?: string | number): string {
  return isDisabled(status) ? '停用' : '启用'
}

function statusTagType(status?: string | number): 'success' | 'danger' | 'info' {
  return isDisabled(status) ? 'danger' : 'success'
}

/* ---------------- 右侧表单 ---------------- */
const panelVisible = ref(false)
const panelMode = ref<'create' | 'edit'>('create')
const parentCode = ref('')
const editingCode = ref('')
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  categoryCode: '',
  name: '',
  sort: undefined as number | undefined,
  status: 1,
  remark: '',
})

const rules = computed<FormRules>(() => ({
  categoryCode:
    panelMode.value === 'create'
      ? [{ required: true, message: '请输入分类编码', trigger: 'blur' }]
      : [],
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
}))

function resetPanel() {
  Object.assign(form, {
    categoryCode: '',
    name: '',
    sort: undefined,
    status: 1,
    remark: '',
  })
}

function openCreateRoot() {
  panelMode.value = 'create'
  parentCode.value = ''
  editingCode.value = ''
  resetPanel()
  panelVisible.value = true
}

function openCreateChild(node: CategoryNode) {
  panelMode.value = 'create'
  parentCode.value = node.categoryCode
  editingCode.value = ''
  resetPanel()
  panelVisible.value = true
}

function openEdit(node: CategoryNode) {
  panelMode.value = 'edit'
  parentCode.value = ''
  editingCode.value = node.categoryCode
  Object.assign(form, {
    categoryCode: node.categoryCode,
    name: node.name || '',
    sort: node.sort,
    status: isDisabled(node.status) ? 0 : 1,
    remark: node.remark || '',
  })
  panelVisible.value = true
}

function cancelPanel() {
  panelVisible.value = false
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (panelMode.value === 'create') {
      await createCategory({
        categoryCode: form.categoryCode.trim(),
        name: form.name.trim(),
        parentCode: parentCode.value || undefined,
        sort: form.sort,
        remark: form.remark || undefined,
      })
      ElMessage.success('创建成功')
    } else {
      await updateCategory(editingCode.value, {
        name: form.name.trim(),
        sort: form.sort,
        status: form.status,
        remark: form.remark || undefined,
      })
      ElMessage.success('保存成功')
    }
    panelVisible.value = false
    void loadTree()
  } catch {
    // 拦截器已提示（超 5 级 / 编码重复等）
  } finally {
    saving.value = false
  }
}

async function remove(node: CategoryNode) {
  try {
    await ElMessageBox.confirm(
      `确定删除分类「${node.name}（${node.categoryCode}）」吗？存在子级或已被引用时将无法删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteCategory(node.categoryCode)
    ElMessage.success('删除成功')
    void loadTree()
  } catch {
    // 拦截器已提示（有子级或被引用 409 中文消息）
  }
}

onMounted(() => {
  void loadTree()
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
    const blob = await fetchCategoryImportTemplate()
    saveBlob(blob, '分类导入模板.csv')
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
    importResult.value = await importCategories(importFile.value)
    ElMessage.success('导入完成')
    void loadTree()
  } catch {
    // 拦截器已提示
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <div class="page-card">
    <div class="page-title">资产分类</div>
    <div class="category-layout">
      <!-- 左侧分类树 -->
      <div class="tree-panel">
        <div class="tree-toolbar">
          <span class="tree-title">分类树</span>
          <span>
            <el-button size="small" plain @click="openImport">导入</el-button>
            <el-button type="primary" size="small" plain @click="openCreateRoot">新增根分类</el-button>
          </span>
        </div>
        <el-tree
          v-loading="treeLoading"
          :data="tree"
          node-key="categoryCode"
          :props="{ label: 'name', children: 'children' }"
          default-expand-all
          :expand-on-click-node="false"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <span class="tree-node-name">{{ data.name }}</span>
              <span class="tree-node-code">{{ data.categoryCode }}</span>
              <el-tag :type="statusTagType(data.status)" size="small" style="margin-left: 6px">
                {{ statusText(data.status) }}
              </el-tag>
              <span class="node-actions">
                <el-button link type="primary" size="small" @click.stop="openCreateChild(data)">新增子级</el-button>
                <el-button link type="primary" size="small" @click.stop="openEdit(data)">编辑</el-button>
                <el-button link type="danger" size="small" @click.stop="remove(data)">删除</el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </div>

      <!-- 右侧表单 -->
      <div class="form-panel">
        <template v-if="panelVisible">
          <div class="form-title">
            {{ panelMode === 'create' ? '新增分类' : '编辑分类' }}
            <el-tag v-if="panelMode === 'create'" size="small" type="info">
              {{ parentCode ? `父级：${parentCode}` : '根级' }}
            </el-tag>
          </div>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="82px">
            <el-form-item label="分类编码" prop="categoryCode">
              <el-input v-model="form.categoryCode" :disabled="panelMode === 'edit'" placeholder="创建后不可修改" />
            </el-form-item>
            <el-form-item label="分类名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item v-if="panelMode === 'edit'" label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item>
              <el-button @click="cancelPanel">取消</el-button>
              <el-button type="primary" :loading="saving" @click="save">保存</el-button>
            </el-form-item>
          </el-form>
        </template>
        <el-empty v-else description="选择左侧操作新增或编辑分类" :image-size="80" />
      </div>
    </div>

    <!-- 批量导入 -->
    <el-dialog v-model="importVisible" title="批量导入分类" width="620px" destroy-on-close>
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
      <div class="import-hint">父分类编码可空=根级；子级父编码须已存在或排在本行之前（最多五级）。</div>
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
.category-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.tree-panel {
  flex: 1;
  min-width: 380px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px;
}

.tree-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.tree-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.tree-node {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  padding-right: 8px;
}

.tree-node-name {
  color: var(--el-text-color-primary);
}

.tree-node-code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: 6px;
}

.node-actions {
  margin-left: auto;
  visibility: hidden;
  white-space: nowrap;
}

.tree-panel :deep(.el-tree-node__content:hover) .node-actions {
  visibility: visible;
}

.form-panel {
  width: 400px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 16px;
  min-height: 300px;
}

.form-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
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
</style>
