<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { PageResult } from '@/api/types'
import { saveBlob } from '@/api/asset'
import { exportWorkorders, fetchWorkorders, WO_PRIORITIES, WO_STATUSES, WO_TYPES } from '@/api/workorder'
import type { WorkorderItem } from '@/api/workorder'
import { isSlaOverdue, priorityTag, statusTag } from './wo-shared'
import WorkorderCreateDialog from './WorkorderCreateDialog.vue'
import WorkorderKanbanBoard from './WorkorderKanbanBoard.vue'

const auth = useAuthStore()
const router = useRouter()

/** workorders 模块写权限（创建工单按钮显隐） */
const canWrite = computed(() => !!auth.modules['workorders']?.write)
/** 工作流/SLA 配置页仅管理员可进（后端同样按 403 兜底） */
const isAdmin = computed(() => !!auth.user?.isAdmin)

const query = reactive({
  page: 1,
  size: 20,
  status: '',
  woType: '',
  priority: '',
  keyword: '',
  tab: '',
  handler: '',
  applyFrom: '',
  applyTo: '',
  slaOverdue: '' as '' | 'Y' | 'N',
})
const total = ref(0)
const records = ref<WorkorderItem[]>([])
const listLoading = ref(false)
const exporting = ref(false)

function params() {
  return {
    page: query.page,
    size: query.size,
    status: query.status || undefined,
    woType: query.woType || undefined,
    priority: query.priority || undefined,
    keyword: query.keyword || undefined,
    tab: query.tab || undefined,
    handler: query.handler || undefined,
    applyFrom: query.applyFrom || undefined,
    applyTo: query.applyTo || undefined,
    slaOverdue: query.slaOverdue ? query.slaOverdue === 'Y' : undefined,
  }
}

/** v3.5 视图模式：表格 / 看板（看板支持拖拽流转，UI-08） */
const viewMode = ref<'table' | 'kanban'>('table')

/** 看板筛选条件：与表格同口径；分页由看板自理（最多 200 条） */
const kanbanFilters = computed(() => {
  const p = params() as Record<string, unknown>
  const rest: Record<string, unknown> = {}
  Object.keys(p).forEach((k) => {
    if (k !== 'page' && k !== 'size') rest[k] = p[k]
  })
  return rest
})

async function load() {
  listLoading.value = true
  try {
    const res: PageResult<WorkorderItem> = await fetchWorkorders(params())
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
  query.status = ''
  query.woType = ''
  query.priority = ''
  query.keyword = ''
  query.tab = ''
  query.handler = ''
  query.applyFrom = ''
  query.applyTo = ''
  query.slaOverdue = ''
  search()
}

/** 「待我审批」快捷开关（tab=approve） */
function toggleApprove() {
  query.tab = query.tab === 'approve' ? '' : 'approve'
  search()
}

/** SLA 超期快捷开关（与后端 SlaPolicy 同一口径） */
function toggleSlaOverdue() {
  query.slaOverdue = query.slaOverdue === 'Y' ? '' : 'Y'
  search()
}

/** 导出当前筛选结果为 CSV（与列表同口径，最多 10000 行） */
async function doExport() {
  exporting.value = true
  try {
    const blob = await exportWorkorders({ ...params(), page: 1, size: 200 })
    const ymd = new Date().toISOString().slice(0, 10)
    saveBlob(blob, `工单_${ymd}.csv`)
  } catch {
    // 拦截器已提示
  } finally {
    exporting.value = false
  }
}

/** 详情走独立深链 /workorders/:woNo */
function openDetail(row: WorkorderItem) {
  void router.push(`/workorders/${encodeURIComponent(row.woNo)}`)
}

function goSettings() {
  void router.push('/workorders/settings')
}

const createRef = ref<InstanceType<typeof WorkorderCreateDialog> | null>(null)
function openCreate() {
  createRef.value?.open()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">
      工单管理
      <span class="page-title-side">
        <el-button v-if="isAdmin" size="small" plain @click="goSettings">
          工作流 / SLA 配置
        </el-button>
      </span>
    </div>

    <!-- 顶部筛选 -->
    <div class="page-toolbar">
      <el-select
        v-model="query.status"
        placeholder="状态"
        clearable
        style="width: 120px"
        @change="search"
      >
        <el-option v-for="s in WO_STATUSES" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select
        v-model="query.woType"
        placeholder="类型"
        clearable
        style="width: 120px"
        @change="search"
      >
        <el-option v-for="t in WO_TYPES" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select
        v-model="query.priority"
        placeholder="优先级"
        clearable
        style="width: 110px"
        @change="search"
      >
        <el-option v-for="p in WO_PRIORITIES" :key="p" :label="p" :value="p" />
      </el-select>
      <el-input
        v-model="query.handler"
        placeholder="处理人"
        clearable
        style="width: 130px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-date-picker
        v-model="query.applyFrom"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="申请时间起"
        style="width: 150px"
        @change="search"
      />
      <el-date-picker
        v-model="query.applyTo"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="申请时间止"
        style="width: 150px"
        @change="search"
      />
      <el-select
        v-model="query.slaOverdue"
        placeholder="SLA"
        clearable
        style="width: 120px"
        @change="search"
      >
        <el-option label="仅超期" value="Y" />
        <el-option label="未超期" value="N" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="工单编号 / 标题 / 申请人"
        clearable
        style="width: 210px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <el-button :type="query.tab === 'approve' ? 'primary' : 'default'" @click="toggleApprove">
        待我审批
      </el-button>
      <el-button
        :type="query.slaOverdue === 'Y' ? 'danger' : 'default'"
        plain
        @click="toggleSlaOverdue"
      >
        仅看超期
      </el-button>
      <div style="flex: 1"></div>
      <el-radio-group v-model="viewMode" size="small">
        <el-radio-button value="table">表格</el-radio-button>
        <el-radio-button value="kanban">看板</el-radio-button>
      </el-radio-group>
      <el-button :loading="exporting" @click="doExport">导出 CSV</el-button>
      <el-button v-if="canWrite" type="primary" @click="openCreate">创建工单</el-button>
    </div>

    <!-- 列表 / 看板（v3.5 视图切换） -->
    <template v-if="viewMode === 'table'">
    <el-table
      v-loading="listLoading"
      :data="records"
      border
      stripe
      row-key="woNo"
      @row-click="(row: WorkorderItem) => openDetail(row)"
    >
      <el-table-column prop="woNo" label="工单编号" min-width="160" fixed="left" show-overflow-tooltip />
      <el-table-column prop="woType" label="类型" width="100">
        <template #default="{ row }">{{ row.woType || '-' }}</template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ row.status || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优先级" width="80">
        <template #default="{ row }">
          <el-tag :type="priorityTag(row.priority)" size="small">{{ row.priority || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="applicant" label="申请人" width="100">
        <template #default="{ row }">{{ row.applicant || '-' }}</template>
      </el-table-column>
      <el-table-column prop="handler" label="处理人" width="100">
        <template #default="{ row }">{{ row.handler || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="申请时间" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.createdAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="SLA解决截止" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.slaResolveDeadline" :class="{ 'sla-overdue': isSlaOverdue(row) }">
            {{ row.slaResolveDeadline }}
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
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
    </template>

    <!-- v3.5 看板：按状态分列，拖拽流转（UI-08） -->
    <WorkorderKanbanBoard v-else :filters="kanbanFilters" @open="openDetail" />

    <WorkorderCreateDialog ref="createRef" @created="() => { query.page = 1; load() }" />
  </div>
</template>

<style scoped>
.page-title-side {
  float: right;
}

.sla-overdue {
  color: #f56c6c;
  font-weight: 600;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
