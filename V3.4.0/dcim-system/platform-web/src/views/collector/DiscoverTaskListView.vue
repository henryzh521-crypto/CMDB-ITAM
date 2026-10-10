<script setup lang="ts">
import { reactive, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  DISCOVER_PROTOCOLS,
  DISCOVER_RESULT_STATUSES,
  DISCOVER_TASK_STATUSES,
  claimDiscoverResult,
  createBmcCredential,
  createDiscoverTask,
  deleteDiscoverTask,
  fetchBmcCredentials,
  fetchDiscoverResults,
  fetchDiscoverTasks,
  ignoreDiscoverResult,
  runDiscoverTask,
} from '@/api/collector'
import type {
  BmcCredentialItem,
  DiscoverResultItem,
  DiscoverTaskItem,
} from '@/api/collector'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const canWrite = () => auth.canWrite('discovery')

/* ---------------- 任务列表 ---------------- */
const query = reactive({ page: 1, size: 20, status: '', keyword: '' })
const total = ref(0)
const records = ref<DiscoverTaskItem[]>([])
const listLoading = ref(false)

async function load() {
  listLoading.value = true
  try {
    const res = await fetchDiscoverTasks({
      page: query.page,
      size: query.size,
      status: query.status || undefined,
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

/* ---------------- 创建任务 ---------------- */
const createVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  cidr: '',
  protocol: 'REDFISH',
  credentialId: undefined as number | undefined,
  regionCode: '',
})

async function openCreate() {
  Object.assign(form, { name: '', cidr: '', protocol: 'REDFISH', credentialId: undefined, regionCode: '' })
  if (credentials.value.length === 0) await loadCredentials()
  createVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await createDiscoverTask({
      name: form.name.trim(),
      cidr: form.cidr.trim(),
      protocol: form.protocol,
      credentialId: form.credentialId,
      regionCode: form.regionCode.trim() || undefined,
    })
    ElMessage.success('创建成功，等待采集器领取执行')
    createVisible.value = false
    void load()
  } catch {
    // 拦截器已提示（/16 过大、凭据不存在等）
  } finally {
    saving.value = false
  }
}

async function runNow(row: DiscoverTaskItem) {
  try {
    await ElMessageBox.confirm(
      `将任务「${row.taskNo}」重置为待执行，采集器会重新领取扫描。确认执行？`,
      '立即执行',
      { type: 'info' },
    )
  } catch {
    return
  }
  try {
    await runDiscoverTask(row.taskNo)
    ElMessage.success('已触发，等待采集器领取')
    void load()
  } catch {
    // 拦截器已提示
  }
}

async function remove(row: DiscoverTaskItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除任务「${row.taskNo}」吗？任务下的发现结果将一并删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteDiscoverTask(row.taskNo)
    ElMessage.success('删除成功')
    void load()
  } catch {
    // 拦截器已提示（扫描中 409 等）
  }
}

/* ---------------- 结果 / 待认领池 ---------------- */
const resultDrawer = ref(false)
const rQuery = reactive({ page: 1, size: 20, status: '待认领', keyword: '', taskId: undefined as number | undefined })
const rTotal = ref(0)
const rRecords = ref<DiscoverResultItem[]>([])
const rLoading = ref(false)

function openResults(row?: DiscoverTaskItem) {
  if (row) rQuery.taskId = row.id
  resultDrawer.value = true
  rQuery.page = 1
  void loadResults()
}

async function loadResults() {
  rLoading.value = true
  try {
    const res = await fetchDiscoverResults({
      page: rQuery.page,
      size: rQuery.size,
      taskId: rQuery.taskId,
      status: rQuery.status || undefined,
      keyword: rQuery.keyword || undefined,
    })
    rTotal.value = res.total ?? 0
    rRecords.value = res.records ?? []
  } catch {
    // 拦截器已提示
  } finally {
    rLoading.value = false
  }
}

function rSearch() {
  rQuery.page = 1
  void loadResults()
}

/* ---------------- 认领 / 忽略 ---------------- */
const claimVisible = ref(false)
const claimingRow = ref<DiscoverResultItem | null>(null)
const claimSaving = ref(false)
const claimForm = reactive({
  name: '',
  categoryCode: '',
  modelCode: '',
  regionCode: '',
})

/** decision.conflicts 非空 = 人工裁决（需建档字段）；否则 SN 命中一键确认 */
function needManual(row: DiscoverResultItem): boolean {
  const conflicts = row.decision?.conflicts
  return Array.isArray(conflicts) && conflicts.length > 0
}

function openClaim(row: DiscoverResultItem) {
  claimingRow.value = row
  Object.assign(claimForm, { name: '', categoryCode: '', modelCode: '', regionCode: '' })
  claimVisible.value = true
}

async function saveClaim() {
  if (!claimingRow.value) return
  if (needManual(claimingRow.value) && !claimForm.name.trim()) {
    ElMessage.warning('人工认领建档需提供资产名称')
    return
  }
  claimSaving.value = true
  try {
    const res = await claimDiscoverResult(claimingRow.value.id, {
      name: claimForm.name.trim() || undefined,
      categoryCode: claimForm.categoryCode.trim() || undefined,
      modelCode: claimForm.modelCode.trim() || undefined,
      regionCode: claimForm.regionCode.trim() || undefined,
    })
    ElMessage.success(
      res.status === '已匹配' ? '已确认匹配并回填资产' : '已认领建档（资产进入待上架）',
    )
    claimVisible.value = false
    void loadResults()
    void load()
  } catch {
    // 拦截器已提示（缺失名称 400 / 已处理 409）
  } finally {
    claimSaving.value = false
  }
}

async function ignore(row: DiscoverResultItem) {
  try {
    await ElMessageBox.confirm(`确定忽略 ${row.bmcIp}（SN: ${row.sn || '无'}）的发现结果吗？`, '忽略确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await ignoreDiscoverResult(row.id)
    ElMessage.success('已忽略')
    void loadResults()
    void load()
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- 凭据 ---------------- */
const credVisible = ref(false)
const credentials = ref<BmcCredentialItem[]>([])
const credSaving = ref(false)
const credForm = reactive({ credName: '', username: '', password: '' })

async function loadCredentials() {
  try {
    credentials.value = await fetchBmcCredentials()
  } catch {
    // 拦截器已提示
  }
}

function openCredentials() {
  credVisible.value = true
  void loadCredentials()
}

async function saveCredential() {
  if (!credForm.credName.trim() || !credForm.username.trim() || !credForm.password) {
    ElMessage.warning('凭据名称/用户名/密码均不能为空')
    return
  }
  credSaving.value = true
  try {
    await createBmcCredential({
      credName: credForm.credName.trim(),
      username: credForm.username.trim(),
      password: credForm.password,
    })
    ElMessage.success('凭据已创建（密码加密存储，不可回显）')
    Object.assign(credForm, { credName: '', username: '', password: '' })
    void loadCredentials()
  } catch {
    // 拦截器已提示（重名 409）
  } finally {
    credSaving.value = false
  }
}

const taskStatusType = (s: string) =>
  s === '扫描中' ? 'warning' : s === '已完成' ? 'success' : s === '失败' ? 'danger' : 'info'
const resultStatusType = (s: string) =>
  s === '待认领' ? 'warning' : s === '已匹配' || s === '已认领' ? 'success' : 'info'

onMounted(() => {
  void load()
  void loadCredentials()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">自动发现</div>

    <!-- 顶部筛选 -->
    <div class="page-toolbar">
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="search">
        <el-option v-for="s in DISCOVER_TASK_STATUSES" :key="s" :label="s" :value="s" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="任务号 / 名称 / CIDR"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" plain @click="search">查询</el-button>
      <div style="flex: 1"></div>
      <el-button @click="openCredentials">带外凭据</el-button>
      <el-button @click="openResults()">待认领池</el-button>
      <el-button v-if="canWrite()" type="primary" @click="openCreate">新建任务</el-button>
    </div>

    <!-- 任务列表 -->
    <el-table v-loading="listLoading" :data="records" border stripe row-key="taskNo">
      <el-table-column prop="taskNo" label="任务号" min-width="130" fixed="left" />
      <el-table-column prop="name" label="任务名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="cidr" label="网段" min-width="130" />
      <el-table-column prop="protocol" label="协议" width="100" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="taskStatusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度（IP）" width="110">
        <template #default="{ row }">{{ row.scannedIps ?? 0 }} / {{ row.totalIps ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="发现/匹配/待认领" width="140">
        <template #default="{ row }">
          {{ row.foundCount }} / {{ row.matchedCount }} /
          <span :style="row.pendingCount > 0 ? 'color:#e6a23c;font-weight:600' : ''">{{ row.pendingCount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="regionCode" label="区域" width="80">
        <template #default="{ row }">{{ row.regionCode || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createdBy" label="创建人" width="90">
        <template #default="{ row }">{{ row.createdBy || '-' }}</template>
      </el-table-column>
      <el-table-column prop="lastRunAt" label="最近执行" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.lastRunAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openResults(row)">结果</el-button>
          <template v-if="canWrite()">
            <el-button link type="primary" @click="runNow(row)">立即执行</el-button>
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

    <!-- 新建任务 -->
    <el-dialog v-model="createVisible" title="新建发现任务" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.name" placeholder="如 庆阳-QY01-1号网段扫描" />
        </el-form-item>
        <el-form-item label="网段 CIDR" required>
          <el-input v-model="form.cidr" placeholder="10.0.0.0/24（仅支持 /24 至 /32）" />
        </el-form-item>
        <el-form-item label="协议" required>
          <el-radio-group v-model="form.protocol">
            <el-radio v-for="p in DISCOVER_PROTOCOLS" :key="p" :value="p">{{ p }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="带外凭据">
          <el-select v-model="form.credentialId" placeholder="IPMI 协议必选；Redfish 可选" clearable style="width: 100%">
            <el-option v-for="c in credentials" :key="c.id" :label="`${c.credName}（${c.username}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="区域编码">
          <el-input v-model="form.regionCode" placeholder="QY / WLCB / SG（可空）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">创建</el-button>
      </template>
    </el-dialog>

    <!-- 待认领池 / 结果 -->
    <el-drawer v-model="resultDrawer" title="发现结果 / 待认领池" size="72%">
      <div class="page-toolbar" style="margin-bottom: 12px">
        <el-select v-model="rQuery.status" placeholder="状态" clearable style="width: 130px" @change="rSearch">
          <el-option v-for="s in DISCOVER_RESULT_STATUSES" :key="s" :label="s" :value="s" />
        </el-select>
        <el-input
          v-model="rQuery.keyword"
          placeholder="IP / SN / 型号 / 资产号"
          clearable
          style="width: 200px"
          @keyup.enter="rSearch"
          @clear="rSearch"
        />
        <el-button type="primary" plain @click="rSearch">查询</el-button>
        <div style="flex: 1"></div>
        <el-button @click="rQuery.taskId = undefined; rSearch()">查看全部任务</el-button>
      </div>
      <el-table v-loading="rLoading" :data="rRecords" border stripe>
        <el-table-column prop="bmcIp" label="BMC IP" min-width="120" fixed="left" />
        <el-table-column prop="sn" label="SN" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sn || '-' }}</template>
        </el-table-column>
        <el-table-column label="厂商 / 型号" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.manufacturer || '-' }} / {{ row.model || '-' }}</template>
        </el-table-column>
        <el-table-column prop="assetNo" label="匹配资产" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.assetNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="resultStatusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="IRE 裁决" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.decision">{{ row.decision.action || row.decision.matchType || '-' }}
              <el-tooltip v-if="needManual(row)" content="存在冲突字段，需人工认领建档">
                <el-tag type="danger" size="small" style="margin-left:4px">冲突</el-tag>
              </el-tooltip>
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="发现时间" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createdAt || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="canWrite()" label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === '待认领'">
              <el-button link type="primary" @click="openClaim(row)">
                {{ needManual(row) ? '人工认领' : '确认匹配' }}
              </el-button>
              <el-button link type="danger" @click="ignore(row)">忽略</el-button>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="page-pagination">
        <el-pagination
          v-model:current-page="rQuery.page"
          v-model:page-size="rQuery.size"
          :total="rTotal"
          layout="total, prev, pager, next"
          @current-change="loadResults"
        />
      </div>
    </el-drawer>

    <!-- 认领弹窗 -->
    <el-dialog
      v-model="claimVisible"
      :title="claimingRow && needManual(claimingRow) ? '人工认领建档' : '确认匹配'"
      width="520px"
      destroy-on-close
    >
      <el-alert
        v-if="claimingRow && needManual(claimingRow)"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
        title="SN 与台账不一致（IRE 冲突），确认后将按以下信息新建资产档案"
      />
      <el-descriptions v-if="claimingRow" :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="BMC IP">{{ claimingRow.bmcIp }}</el-descriptions-item>
        <el-descriptions-item label="SN">{{ claimingRow.sn || '-' }}</el-descriptions-item>
        <el-descriptions-item label="厂商">{{ claimingRow.manufacturer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ claimingRow.model || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="资产名称" :required="claimingRow ? needManual(claimingRow) : false">
          <el-input v-model="claimForm.name" placeholder="人工认领建档必填" />
        </el-form-item>
        <el-form-item label="分类编码">
          <el-input v-model="claimForm.categoryCode" placeholder="如 SRV（可空）" />
        </el-form-item>
        <el-form-item label="型号编码">
          <el-input v-model="claimForm.modelCode" placeholder="可空" />
        </el-form-item>
        <el-form-item label="区域编码">
          <el-input v-model="claimForm.regionCode" placeholder="QY / WLCB / SG（可空）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="claimVisible = false">取消</el-button>
        <el-button type="primary" :loading="claimSaving" @click="saveClaim">确认</el-button>
      </template>
    </el-dialog>

    <!-- 凭据管理 -->
    <el-dialog v-model="credVisible" title="带外凭据（密码加密存储，不可回显）" width="640px" destroy-on-close>
      <el-table :data="credentials" border stripe size="small" style="margin-bottom: 16px">
        <el-table-column prop="credName" label="凭据名称" min-width="140" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="createdAt" label="创建时间" min-width="160" show-overflow-tooltip />
      </el-table>
      <template v-if="canWrite()">
        <el-divider content-position="left">新建凭据</el-divider>
        <el-form label-width="90px">
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="名称">
                <el-input v-model="credForm.credName" placeholder="唯一" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="用户名">
                <el-input v-model="credForm.username" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="密码">
                <el-input v-model="credForm.password" type="password" show-password />
              </el-form-item>
            </el-col>
          </el-row>
          <el-button type="primary" :loading="credSaving" @click="saveCredential">保存凭据</el-button>
        </el-form>
      </template>
    </el-dialog>
  </div>
</template>
