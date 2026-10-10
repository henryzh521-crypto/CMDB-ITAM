<script setup lang="ts">
import { reactive, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  collectEndpointNow,
  createBmcEndpoint,
  deleteBmcEndpoint,
  fetchBmcCredentials,
  fetchBmcEndpoints,
  fetchEndpointSnapshots,
  importBmcEndpoints,
  updateBmcEndpoint,
} from '@/api/collector'
import type {
  BmcCredentialItem,
  BmcEndpointItem,
  EndpointImportResult,
  HwSnapshotItem,
} from '@/api/collector'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const canWrite = () => auth.canWrite('discovery')

/* ---------------- 列表查询 ---------------- */
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  disabled: '' as '' | 'true' | 'false',
  unmanaged: false,
})
const total = ref(0)
const records = ref<BmcEndpointItem[]>([])
const listLoading = ref(false)

async function load() {
  listLoading.value = true
  try {
    const res = await fetchBmcEndpoints({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      disabled: query.disabled === '' ? undefined : query.disabled === 'true',
      unmanaged: query.unmanaged || undefined,
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
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  bmcIp: '',
  assetNo: '',
  credentialId: undefined as number | undefined,
  collectIntervalMin: 60,
  disabled: false,
})
const credentials = ref<BmcCredentialItem[]>([])

async function loadCredentials() {
  try {
    credentials.value = await fetchBmcCredentials()
  } catch {
    // 拦截器已提示
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { bmcIp: '', assetNo: '', credentialId: undefined, collectIntervalMin: 60, disabled: false })
  void loadCredentials()
  dialogVisible.value = true
}

function openEdit(row: BmcEndpointItem) {
  editingId.value = row.id
  Object.assign(form, {
    bmcIp: row.bmcIp,
    assetNo: row.assetNo || '',
    credentialId: row.credentialId,
    collectIntervalMin: row.collectIntervalMin,
    disabled: row.disabled,
  })
  void loadCredentials()
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      assetNo: form.assetNo.trim() || undefined,
      credentialId: form.credentialId,
      collectIntervalMin: form.collectIntervalMin,
      disabled: form.disabled,
    }
    if (editingId.value) {
      await updateBmcEndpoint(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createBmcEndpoint({ ...payload, bmcIp: form.bmcIp.trim() })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    void load()
  } catch {
    // 拦截器已提示（重复 409 / 资产或凭据不存在 400 / 间隔越界 400）
  } finally {
    saving.value = false
  }
}

async function remove(row: BmcEndpointItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除端点 ${row.bmcIp} 吗？已有快照历史的端点不允许删除（可停用）。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteBmcEndpoint(row.id)
    ElMessage.success('删除成功')
    void load()
  } catch {
    // 拦截器已提示
  }
}

async function collectNow(row: BmcEndpointItem) {
  try {
    await collectEndpointNow(row.id)
    ElMessage.success('已触发，等待采集器领取')
    void load()
  } catch {
    // 拦截器已提示（未纳管/停用 400）
  }
}

/* ---------------- 批量导入 ---------------- */
const importVisible = ref(false)
const importText = ref('')
const importing = ref(false)
const importResult = ref<EndpointImportResult | null>(null)

async function doImport() {
  const rows = importText.value
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#'))
    .map((l) => {
      const [bmcIp, assetNo] = l.split(/[,,]/)
      return { bmcIp: (bmcIp || '').trim(), assetNo: (assetNo || '').trim() || undefined }
    })
    .filter((r) => r.bmcIp)
  if (rows.length === 0) {
    ElMessage.warning('未解析到有效行（每行：bmcIp,assetNo）')
    return
  }
  importing.value = true
  try {
    importResult.value = await importBmcEndpoints(rows)
    void load()
  } catch {
    // 拦截器已提示
  } finally {
    importing.value = false
  }
}

/* ---------------- 快照历史 ---------------- */
const snapDrawer = ref(false)
const snapEndpoint = ref<BmcEndpointItem | null>(null)
const snapQuery = reactive({ page: 1, size: 10 })
const snapTotal = ref(0)
const snapRecords = ref<HwSnapshotItem[]>([])
const snapLoading = ref(false)

function openSnapshots(row: BmcEndpointItem) {
  snapEndpoint.value = row
  snapQuery.page = 1
  snapDrawer.value = true
  void loadSnapshots()
}

async function loadSnapshots() {
  if (!snapEndpoint.value) return
  snapLoading.value = true
  try {
    const res = await fetchEndpointSnapshots(snapEndpoint.value.id, snapQuery)
    snapTotal.value = res.total ?? 0
    snapRecords.value = res.records ?? []
  } catch {
    // 拦截器已提示
  } finally {
    snapLoading.value = false
  }
}

function specDiffCount(row: HwSnapshotItem): number {
  return row.specDiff ? Object.keys(row.specDiff).length : 0
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">带外档案（BMC 端点）</div>

    <!-- 顶部筛选 -->
    <div class="page-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="BMC IP / 资产编号"
        clearable
        style="width: 200px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="query.disabled" placeholder="启用状态" clearable style="width: 120px" @change="search">
        <el-option label="启用" value="false" />
        <el-option label="停用" value="true" />
      </el-select>
      <el-checkbox v-model="query.unmanaged" label="仅未纳管" @change="search" />
      <el-button type="primary" plain @click="search">查询</el-button>
      <div style="flex: 1"></div>
      <el-button v-if="canWrite()" @click="importVisible = true">批量导入</el-button>
      <el-button v-if="canWrite()" type="primary" @click="openCreate">登记端点</el-button>
    </div>

    <!-- 列表 -->
    <el-table v-loading="listLoading" :data="records" border stripe>
      <el-table-column prop="bmcIp" label="BMC IP" min-width="130" fixed="left" />
      <el-table-column label="纳管资产" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.assetNo">{{ row.assetNo }}<span v-if="row.assetName" style="color:#909399">（{{ row.assetName }}）</span></template>
          <el-tag v-else type="info" size="small">未纳管</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="凭据" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.credentialName || '-' }}</template>
      </el-table-column>
      <el-table-column label="协议版本" width="130">
        <template #default="{ row }">
          <span v-if="row.redfishVersion">RF {{ row.redfishVersion }}</span>
          <span v-else-if="row.ipmiVersion">IPMI {{ row.ipmiVersion }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="collectIntervalMin" label="间隔(分)" width="85" />
      <el-table-column label="连续失败" width="90">
        <template #default="{ row }">
          <span :style="row.consecutiveFailures >= 3 ? 'color:#f56c6c;font-weight:600' : ''">
            {{ row.consecutiveFailures }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="最近采集" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.lastCollectAt">
            {{ row.lastCollectAt }}
            <el-tag :type="row.lastCollectOk ? 'success' : 'danger'" size="small" style="margin-left:4px">
              {{ row.lastCollectOk ? '成功' : '失败' }}
            </el-tag>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.disabled ? 'info' : 'success'" size="small">{{ row.disabled ? '停用' : '启用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openSnapshots(row)">快照</el-button>
          <template v-if="canWrite()">
            <el-button link type="primary" @click="collectNow(row)">立即采集</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
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

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑端点' : '登记带外端点'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" label-width="110px">
        <el-form-item label="BMC IP" required>
          <el-input v-model="form.bmcIp" :disabled="!!editingId" placeholder="如 10.0.0.101" />
        </el-form-item>
        <el-form-item label="纳管资产">
          <el-input v-model="form.assetNo" placeholder="资产编号（留空=未纳管，不参与采集）" />
        </el-form-item>
        <el-form-item label="带外凭据">
          <el-select v-model="form.credentialId" placeholder="选择凭据" clearable style="width: 100%">
            <el-option v-for="c in credentials" :key="c.id" :label="`${c.credName}（${c.username}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="采集间隔(分)">
          <el-input-number v-model="form.collectIntervalMin" :min="5" :max="1440" controls-position="right" />
        </el-form-item>
        <el-form-item label="停用">
          <el-switch v-model="form.disabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="importVisible" title="批量导入端点（部分成功语义）" width="640px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 10px"
        title="每行一条：bmcIp,assetNo（# 开头行为注释）；bmcIp 已存在则更新资产绑定，资产不存在该行报错不阻断"
      />
      <el-input
        v-model="importText"
        type="textarea"
        :rows="10"
        placeholder="10.0.0.101,SRV-QY26-2026-00001&#10;10.0.0.102,SRV-QY26-2026-00002"
      />
      <div v-if="importResult" style="margin-top: 10px">
        <el-alert :type="importResult.errors.length ? 'warning' : 'success'" :closable="false">
          <template #title>
            新建 {{ importResult.created }} 条，更新 {{ importResult.updated }} 条，失败 {{ importResult.errors.length }} 行
          </template>
          <div v-for="e in importResult.errors" :key="e" style="font-size: 12px">{{ e }}</div>
        </el-alert>
      </div>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">导入</el-button>
      </template>
    </el-dialog>

    <!-- 快照历史 -->
    <el-drawer v-model="snapDrawer" :title="`配置快照历史 — ${snapEndpoint?.bmcIp || ''}`" size="62%">
      <el-table v-loading="snapLoading" :data="snapRecords" border stripe>
        <el-table-column prop="collectedAt" label="采集时间" min-width="160" show-overflow-tooltip />
        <el-table-column prop="protocol" label="协议" width="90">
          <template #default="{ row }">{{ row.protocol || '-' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.ok ? 'success' : 'danger'" size="small">{{ row.ok ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规格漂移" width="90">
          <template #default="{ row }">
            <el-tag v-if="specDiffCount(row) > 0" type="warning" size="small">{{ specDiffCount(row) }} 项</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="差异明细" min-width="220">
          <template #default="{ row }">
            <template v-if="row.specDiff">
              <div v-for="(v, k) in row.specDiff" :key="k" style="font-size: 12px; color: #e6a23c">
                {{ k }}：{{ v.expected ?? '-' }} → {{ v.actual ?? '-' }}
              </div>
            </template>
            <span v-else-if="row.error" style="color:#f56c6c;font-size:12px">{{ row.error }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="page-pagination">
        <el-pagination
          v-model:current-page="snapQuery.page"
          v-model:page-size="snapQuery.size"
          :total="snapTotal"
          layout="total, prev, pager, next"
          @current-change="loadSnapshots"
        />
      </div>
    </el-drawer>
  </div>
</template>
