<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import http from '@/api/client'
import { saveBlob } from '@/api/asset'
import type { AuditLogItem, AuditLogOptions, ModuleInfo, PageResult } from '@/api/types'

/* ---------------- 列表查询（仅管理员，非管理员由路由与后端双重拦截） ---------------- */
const query = reactive({
  page: 1,
  size: 20,
  actor: '',
  module: '',
  action: '',
  objType: '',
  objId: '',
  ip: '',
  keyword: '',
})
/** [起, 止]，value-format=YYYY-MM-DD（止由后端补 23:59:59） */
const dateRange = ref<string[] | null>(null)

const total = ref(0)
const records = ref<AuditLogItem[]>([])
const listLoading = ref(false)
const exporting = ref(false)

const options = ref<AuditLogOptions>({ modules: [], actions: [], objTypes: [], actors: [] })
const moduleMap = ref<Record<string, string>>({})

/** 模块中文名走 /api/modules（权威清单），未注册的 code 原样显示 */
function moduleLabel(code?: string): string {
  if (!code) return '-'
  return moduleMap.value[code] || code
}

/** 动作中文名：英文动作码本地映射，中文动作（如「手动评分」）原样显示 */
const ACTION_LABEL: Record<string, string> = {
  CREATE: '新增',
  BATCH_CREATE: '批量新增',
  UPDATE: '编辑',
  BATCH_UPDATE: '批量编辑',
  DELETE: '删除',
  BATCH_DELETE: '批量删除',
  IMPORT: '导入',
  EXPORT: '导出',
  LOGIN: '登录成功',
  LOGIN_FAIL: '登录失败',
  LOGOUT: '登出',
  CHANGE_PWD: '修改密码',
  STATUS_CHANGE: '状态变更',
  BATCH_STATUS_CHANGE: '批量状态变更',
  TRANSITION: '工单流转',
  COMMENT: '工单评论',
  REMIND: '工单催办',
  ADD_ITEM: '工单加项',
  DELETE_ITEM: '工单删项',
  UPDATE_ITEM: '工单改项',
  UPDATE_SLA: '修改SLA',
  UPDATE_WORKFLOW: '修改流程',
  UPDATE_REPLACEMENTS: '修改替换件',
  CAPITALIZE: '转固',
  DEPRECIATE: '计提折旧',
  DISPOSE: '处置',
  IMPAIR: '减值',
  WRITE_OFF: '报废',
  SCAN: '扫码',
  START: '开始',
  CONFIRM: '确认',
  CANCEL: '取消',
  COMPLETE: '完成',
}

function actionLabel(action?: string): string {
  if (!action) return '-'
  return ACTION_LABEL[action] || action
}

function actionTagType(action?: string): 'success' | 'danger' | 'warning' | 'info' {
  switch (action) {
    case 'DELETE':
    case 'BATCH_DELETE':
    case 'LOGIN_FAIL':
      return 'danger'
    case 'CREATE':
    case 'BATCH_CREATE':
    case 'IMPORT':
      return 'success'
    case 'UPDATE':
    case 'BATCH_UPDATE':
    case 'STATUS_CHANGE':
    case 'BATCH_STATUS_CHANGE':
    case 'CHANGE_PWD':
      return 'warning'
    default:
      return 'info'
  }
}

/** 展开行的入参明细（写入侧已对口令/令牌等敏感字段脱敏） */
function detailText(row: AuditLogItem): string {
  return row.detail && Object.keys(row.detail).length
    ? JSON.stringify(row.detail, null, 2)
    : '（无入参明细）'
}

function listParams(): Record<string, string | number | undefined> {
  return {
    page: query.page,
    size: query.size,
    actor: query.actor || undefined,
    module: query.module || undefined,
    action: query.action || undefined,
    objType: query.objType || undefined,
    objId: query.objId || undefined,
    ip: query.ip || undefined,
    keyword: query.keyword || undefined,
    timeFrom: dateRange.value?.[0] || undefined,
    timeTo: dateRange.value?.[1] || undefined,
  }
}

async function load() {
  listLoading.value = true
  try {
    const res = await http.get<any, PageResult<AuditLogItem>>('/audit-logs', {
      params: listParams(),
    })
    total.value = res.total ?? 0
    records.value = res.records ?? []
  } catch {
    // 拦截器已提示（非管理员 403）
  } finally {
    listLoading.value = false
  }
}

async function loadOptions() {
  try {
    const [opts, mods] = await Promise.all([
      http.get<any, AuditLogOptions>('/audit-logs/options'),
      http.get<any, ModuleInfo[]>('/modules'),
    ])
    options.value = {
      modules: opts?.modules ?? [],
      actions: opts?.actions ?? [],
      objTypes: opts?.objTypes ?? [],
      actors: opts?.actors ?? [],
    }
    moduleMap.value = Object.fromEntries((mods ?? []).map((m) => [m.code, m.name]))
  } catch {
    // 拦截器已提示
  }
}

function search() {
  query.page = 1
  void load()
}

function resetQuery() {
  Object.assign(query, {
    page: 1,
    size: 20,
    actor: '',
    module: '',
    action: '',
    objType: '',
    objId: '',
    ip: '',
    keyword: '',
  })
  dateRange.value = null
  void load()
}

async function doExport() {
  exporting.value = true
  try {
    const res = await http.get<Blob>('/audit-logs/export', {
      params: listParams(),
      responseType: 'blob',
    })
    const d = new Date()
    const ymd = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
    saveBlob(res.data, `操作日志_${ymd}.csv`)
  } catch {
    // 拦截器已提示
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  void loadOptions()
  void load()
})
</script>

<template>
  <div class="page-card">
    <div class="page-toolbar">
      <el-select
        v-model="query.module"
        placeholder="模块"
        clearable
        filterable
        style="width: 150px"
        @change="search"
      >
        <el-option v-for="m in options.modules" :key="m" :label="moduleLabel(m)" :value="m" />
      </el-select>
      <el-select
        v-model="query.action"
        placeholder="动作"
        clearable
        filterable
        style="width: 150px"
        @change="search"
      >
        <el-option v-for="a in options.actions" :key="a" :label="actionLabel(a)" :value="a" />
      </el-select>
      <el-select
        v-model="query.objType"
        placeholder="对象类型"
        clearable
        filterable
        style="width: 150px"
        @change="search"
      >
        <el-option v-for="t in options.objTypes" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select
        v-model="query.actor"
        placeholder="操作人"
        clearable
        filterable
        allow-create
        style="width: 150px"
        @change="search"
      >
        <el-option v-for="a in options.actors" :key="a" :label="a" :value="a" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="时间起"
        end-placeholder="时间止"
        style="width: 250px"
        @change="search"
      />
      <el-input
        v-model="query.keyword"
        placeholder="操作人 / 对象ID"
        clearable
        style="width: 190px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <div style="flex: 1"></div>
      <el-button :loading="exporting" @click="doExport">导出</el-button>
    </div>

    <el-table v-loading="listLoading" :data="records" border stripe>
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="detail-box">
            <div class="detail-title">入参明细（口令 / 令牌 / 密钥等敏感字段已脱敏）</div>
            <pre class="detail-pre">{{ detailText(row) }}</pre>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" min-width="165" />
      <el-table-column prop="actor" label="操作人" min-width="110" show-overflow-tooltip />
      <el-table-column label="动作" min-width="130">
        <template #default="{ row }">
          <el-tag :type="actionTagType(row.action)" size="small">{{ actionLabel(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="模块" min-width="110">
        <template #default="{ row }">{{ moduleLabel(row.module) }}</template>
      </el-table-column>
      <el-table-column prop="objType" label="对象类型" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.objType || '-' }}</template>
      </el-table-column>
      <el-table-column prop="objId" label="对象ID" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.objId || '-' }}</template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" min-width="130">
        <template #default="{ row }">{{ row.ip || '-' }}</template>
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
        @size-change="
          () => {
            query.page = 1
            load()
          }
        "
      />
    </div>
  </div>
</template>

<style scoped>
.detail-box {
  padding: 10px 16px;
  background: #f7f8fa;
}

.detail-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.detail-pre {
  margin: 0;
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--el-text-color-primary);
}
</style>
