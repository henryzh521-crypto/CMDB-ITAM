<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import http from '@/api/client'
import { saveBlob } from '@/api/asset'
import type { CsvImportResult, PageResult, RoleItem, UserItem } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { passwordRule } from '@/utils/password'
import { checkCsvImportSize } from '@/utils/upload'

const auth = useAuthStore()

/* ---------------- 列表查询 ---------------- */
const query = reactive({ page: 1, size: 20, keyword: '' })
const total = ref(0)
const records = ref<UserItem[]>([])
const listLoading = ref(false)

async function load() {
  listLoading.value = true
  try {
    const res = await http.get<any, PageResult<UserItem>>('/users', {
      params: {
        page: query.page,
        size: query.size,
        keyword: query.keyword || undefined,
      },
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

function statusTagType(status?: string | number): 'success' | 'danger' | 'info' {
  if (status === 1 || status === '1') return 'success'
  if (status === 0 || status === '0') return 'danger'
  const s = String(status || '').toUpperCase()
  if (s === 'ENABLED' || s === 'ACTIVE') return 'success'
  if (s === 'DISABLED' || s === 'LOCKED') return 'danger'
  return 'info'
}

function statusLabel(status?: string | number): string {
  if (status === 1 || status === '1') return '启用'
  if (status === 0 || status === '0') return '停用'
  const s = String(status || '').toUpperCase()
  if (s === 'ENABLED' || s === 'ACTIVE') return '启用'
  if (s === 'DISABLED' || s === 'LOCKED') return '停用'
  return String(status || '') || '-'
}

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const editingUsername = ref('')
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  name: '',
  password: '',
  department: '',
  phone: '',
  email: '',
  roleCodes: [] as string[],
})

const rules = computed<FormRules>(() => ({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [
    // 新增必填；编辑时留空 = 不改密码（passwordRule 对空值放行）
    ...(editingUsername.value
      ? []
      : [{ required: true, message: '请输入初始密码', trigger: 'blur' }]),
    passwordRule(() => form.username),
  ],
}))

const roleOptions = ref<RoleItem[]>([])

async function loadRoleOptions() {
  try {
    const res = await http.get<any, PageResult<RoleItem>>('/roles', {
      params: { page: 1, size: 100 },
    })
    roleOptions.value = res.records ?? []
  } catch {
    // 拦截器已提示
  }
}

function openCreate() {
  editingUsername.value = ''
  Object.assign(form, {
    username: '',
    name: '',
    password: '',
    department: '',
    phone: '',
    email: '',
    roleCodes: [],
  })
  dialogVisible.value = true
  void loadRoleOptions()
}

function openEdit(row: UserItem) {
  editingUsername.value = row.username
  Object.assign(form, {
    username: row.username,
    name: row.name,
    password: '',
    department: row.department || '',
    phone: row.phone || '',
    email: row.email || '',
    roleCodes: [...(row.roles || [])],
  })
  dialogVisible.value = true
  void loadRoleOptions()
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingUsername.value) {
      const payload: Record<string, unknown> = {
        name: form.name,
        department: form.department,
        phone: form.phone,
        email: form.email,
        roleCodes: form.roleCodes,
      }
      // 管理员在编辑框填写新密码即重置密码；留空则不修改
      if (form.password) payload.password = form.password
      await http.put(`/users/${encodeURIComponent(editingUsername.value)}`, payload)
      ElMessage.success('保存成功')
    } else {
      await http.post('/users', {
        username: form.username,
        name: form.name,
        password: form.password,
        department: form.department,
        phone: form.phone,
        email: form.email,
        roleCodes: form.roleCodes,
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    void load()
  } catch {
    // 拦截器已提示（无权限 403 等）
  } finally {
    saving.value = false
  }
}

/** v3.4：解除账号级登录锁定（跨 IP 多次失败触发） */
async function unlock(row: UserItem) {
  try {
    await http.post(`/users/${encodeURIComponent(row.username)}/unlock`)
    ElMessage.success(`已解锁 ${row.username}`)
    void load()
  } catch {
    // 拦截器已提示
  }
}

async function remove(row: UserItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除用户「${row.username}」吗？该操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await http.delete(`/users/${encodeURIComponent(row.username)}`)
    ElMessage.success('删除成功')
    void load()
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- 本人改密 ---------------- */
const pwdDialogVisible = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdSaving = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    passwordRule(),
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (error?: any) => void) => {
        if (value !== pwdForm.newPassword) callback(new Error('两次输入的新密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function submitPwd() {
  const valid = await pwdFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  const username = auth.user?.username
  if (!username) {
    ElMessage.error('当前登录信息缺失，请重新登录')
    return
  }
  pwdSaving.value = true
  try {
    await http.put(`/users/${encodeURIComponent(username)}/password`, {
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    ElMessage.success('密码修改成功')
    pwdDialogVisible.value = false
    Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
  } catch {
    // 拦截器已提示
  } finally {
    pwdSaving.value = false
  }
}

const route = useRoute()

onMounted(() => {
  // 全局搜索深链：?keyword=xxx 直接带入筛选
  if (typeof route.query.keyword === 'string') query.keyword = route.query.keyword
  void load()
})

/* ---------------- 导入 / 导出 ---------------- */
const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)
const importResult = ref<CsvImportResult | null>(null)
const exporting = ref(false)

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
    const res = await http.get<Blob>('/users/template', { responseType: 'blob' })
    saveBlob(res.data, '用户导入模板.csv')
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
    const form = new FormData()
    form.append('file', importFile.value)
    importResult.value = await http.post<FormData, CsvImportResult>('/users/import', form)
    ElMessage.success('导入完成')
    void load()
  } catch {
    // 拦截器已提示
  } finally {
    importing.value = false
  }
}

async function doExport() {
  exporting.value = true
  try {
    const res = await http.get<Blob>('/users/export', {
      params: { keyword: query.keyword || undefined },
      responseType: 'blob',
    })
    const d = new Date()
    const ymd = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
    saveBlob(res.data, `用户_${ymd}.csv`)
  } catch {
    // 拦截器已提示
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="page-card">
    <div class="page-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="用户名 / 姓名 / 部门"
        clearable
        style="width: 240px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" plain @click="search">查询</el-button>
      <div style="flex: 1"></div>
      <el-button @click="pwdDialogVisible = true">修改我的密码</el-button>
      <el-button :loading="exporting" @click="doExport">导出</el-button>
      <el-button @click="openImport">导入</el-button>
      <el-button type="primary" @click="openCreate">新增用户</el-button>
    </div>

    <el-table v-loading="listLoading" :data="records" border stripe>
      <el-table-column label="用户名" min-width="130">
        <template #default="{ row }">
          {{ row.username }}
          <el-tag v-if="row.authSource === 'LDAP'" size="small" type="info" effect="plain">目录</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="姓名" min-width="90" />
      <el-table-column prop="department" label="部门" min-width="110" show-overflow-tooltip />
      <el-table-column prop="phone" label="手机号" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          <el-tooltip
            v-if="row.loginLocked"
            :content="`多次登录失败已临时锁定，约 ${Math.ceil((row.loginLockedSeconds || 0) / 60)} 分钟后自动解除；本人常用的终端不受影响`"
            placement="top"
          >
            <el-tag type="danger" size="small" style="margin-left: 4px">锁定</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="140">
        <template #default="{ row }">
          <el-tag
            v-for="r in row.roles || []"
            :key="r"
            size="small"
            type="info"
            style="margin-right: 4px"
          >
            {{ r }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="160" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.loginLocked" link type="warning" @click="unlock(row)">解锁</el-button>
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

    <!-- 新增 / 编辑用户 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingUsername ? '编辑用户' : '新增用户'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            :disabled="!!editingUsername"
            placeholder="登录账号，创建后不可修改"
          />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="editingUsername ? '管理员重置密码请在此填写，留空则不修改' : '初始密码'"
          />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="form.department" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="form.roleCodes"
            multiple
            style="width: 100%"
            placeholder="选择角色（可多选）"
          >
            <el-option
              v-for="r in roleOptions"
              :key="r.roleCode"
              :label="r.name"
              :value="r.roleCode"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 本人改密 -->
    <el-dialog v-model="pwdDialogVisible" title="修改我的密码" width="440px" destroy-on-close>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="submitPwd">确认修改</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="importVisible" title="批量导入用户" width="620px" destroy-on-close>
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
      <div class="import-hint">角色列用分号分隔角色编码，如 REQUESTER;OPS_ENGINEER；初始密码必填（首登强制修改）。</div>
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
  line-height: 1.5;
}
</style>
