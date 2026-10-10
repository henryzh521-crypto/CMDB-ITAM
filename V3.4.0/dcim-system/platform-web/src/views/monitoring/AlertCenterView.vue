<script setup lang="ts">
/**
 * 告警中心：活动/历史告警、级别统计、确认（认领）、转工单、详情（标签 / 历史发作 / 同资产告警）
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  SEVERITY_LABEL,
  SEVERITY_TAG,
  ackAlert,
  ackAlerts,
  fetchAlertDetail,
  fetchAlertStats,
  fetchAlerts,
  ticketAlert,
} from '@/api/monitoring'
import type { AlertItem, AlertStats } from '@/api/monitoring'
import { KB_CATEGORY_LABEL, recommendKb } from '@/api/itsm'
import type { KbRecommend } from '@/api/itsm'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const canWrite = computed(() => auth.canWrite('monitoring'))
const canTicket = computed(() => auth.canWrite('workorders'))

const query = reactive({
  page: 1,
  size: 20,
  status: 'FIRING',
  severity: '',
  keyword: '',
  assetNo: '',
  view: 'active' as 'active' | 'suppressed' | 'all',
})
const records = ref<AlertItem[]>([])
const total = ref(0)
const loading = ref(false)
const stats = ref<AlertStats | null>(null)
const selected = ref<AlertItem[]>([])
let timer: number | undefined
/** v3.5 节流：上次刷新时间（手动/自动都记录），自动刷新至少间隔 25s */
let lastRefreshAt = 0

function fmt(v?: string) {
  return v ? v.replace('T', ' ').slice(0, 19) : '-'
}

function duration(a: AlertItem) {
  const s = new Date(a.startsAt).getTime()
  const e = a.endsAt ? new Date(a.endsAt).getTime() : Date.now()
  const m = Math.max(0, Math.round((e - s) / 60000))
  if (m < 60) return `${m} 分钟`
  if (m < 1440) return `${Math.floor(m / 60)} 小时 ${m % 60} 分`
  return `${Math.floor(m / 1440)} 天 ${Math.floor((m % 1440) / 60)} 小时`
}

/** 列表请求序号：筛选/翻页与 30s 自动刷新并发时，仅接受最后一次请求的响应 */
let loadSeq = 0

async function load() {
  const seq = ++loadSeq
  loading.value = true
  try {
    const res = await fetchAlerts({
      page: query.page,
      size: query.size,
      status: query.status || undefined,
      severity: query.severity || undefined,
      keyword: query.keyword || undefined,
      assetNo: query.assetNo || undefined,
      suppressed: query.view === 'all' ? undefined : query.view === 'suppressed',
    })
    if (seq !== loadSeq) return
    records.value = res.records ?? []
    total.value = res.total ?? 0
  } catch {
    // ignore
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await fetchAlertStats()
  } catch {
    // ignore
  }
}

function search() {
  query.page = 1
  void load()
}

function refresh() {
  lastRefreshAt = Date.now()
  void load()
  void loadStats()
}

async function doAck(a: AlertItem) {
  try {
    const { value } = await ElMessageBox.prompt('确认后该告警不再自动派单，可填写处理备注', '确认告警', {
      inputPlaceholder: '如：已知问题，正在处理',
    })
    await ackAlert(a.id, value || undefined)
    ElMessage.success('已确认')
    refresh()
  } catch {
    // 取消
  }
}

async function doAckBatch() {
  if (!selected.value.length) return
  try {
    const r = await ackAlerts(selected.value.map((a) => a.id))
    ElMessage.success(`已确认 ${r.acked} 条`)
    refresh()
  } catch {
    // ignore
  }
}

async function doTicket(a: AlertItem) {
  try {
    const r = await ticketAlert(a.id)
    ElMessage.success(`已关联工单 ${r.woNo}`)
    refresh()
    if (detail.value?.alert.id === a.id) void openDetail(a.id)
  } catch {
    // ignore
  }
}

/* ---------------- 详情 ---------------- */
const drawer = ref(false)
const detail = ref<{ alert: AlertItem; history: any[]; related?: any[] } | null>(null)
const kbs = ref<KbRecommend[]>([])
async function openDetail(id: number) {
  drawer.value = true
  kbs.value = []
  try {
    detail.value = await fetchAlertDetail(id)
    const a = detail.value.alert
    recommendKb({ alertname: a.alertname, assetNo: a.assetNo, text: a.summary, limit: 5 })
      .then((r) => (kbs.value = r))
      .catch(() => undefined)
  } catch {
    drawer.value = false
  }
}

const labelRows = computed(() =>
  Object.entries(detail.value?.alert.labels || {}).map(([k, v]) => ({ k, v })),
)

onMounted(() => {
  if (typeof route.query.assetNo === 'string') {
    query.assetNo = route.query.assetNo
    query.status = ''
  }
  refresh()
  // v3.5 节流自动刷新（30s 周期）：仅页面可见、详情抽屉未打开、距上次刷新 ≥25s 时触发，
  // 避免阅读详情/刚手动刷新时列表跳动
  timer = window.setInterval(() => {
    if (
      document.visibilityState === 'visible'
      && !drawer.value
      && Date.now() - lastRefreshAt >= 25000
    ) {
      refresh()
    }
  }, 30000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <div class="page-card">
    <div class="page-title">告警中心</div>

    <div class="cards">
      <div class="card" @click="(query.severity = 'critical'), (query.status = 'FIRING'), (query.view = 'active'), search()">
        <div class="num c-danger">{{ stats?.firingBySeverity.critical ?? 0 }}</div>
        <div class="lbl">严重（活动）</div>
      </div>
      <div class="card" @click="(query.severity = 'warning'), (query.status = 'FIRING'), (query.view = 'active'), search()">
        <div class="num c-warning">{{ stats?.firingBySeverity.warning ?? 0 }}</div>
        <div class="lbl">警告（活动）</div>
      </div>
      <div class="card" @click="(query.severity = ''), (query.status = 'FIRING'), (query.view = 'active'), search()">
        <div class="num">{{ stats?.unacked ?? 0 }}</div>
        <div class="lbl">未确认</div>
      </div>
      <div class="card" @click="(query.severity = ''), (query.status = 'FIRING'), (query.view = 'suppressed'), search()">
        <div class="num c-info">{{ stats?.suppressed ?? 0 }}</div>
        <div class="lbl">维护期抑制</div>
      </div>
      <div class="card static">
        <div class="num">{{ stats?.new24h ?? 0 }}</div>
        <div class="lbl">近 24 小时新增</div>
      </div>
      <div class="card static">
        <div class="num">{{ stats?.mttrMinutes ?? 0 }}<small> 分钟</small></div>
        <div class="lbl">平均恢复时长（30 天）</div>
      </div>
    </div>

    <div class="page-toolbar">
      <el-radio-group v-model="query.status" size="default" @change="search">
        <el-radio-button value="FIRING">活动</el-radio-button>
        <el-radio-button value="RESOLVED">已恢复</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>
      <el-select v-model="query.view" style="width: 130px" @change="search">
        <el-option label="需处理" value="active" />
        <el-option label="维护期抑制" value="suppressed" />
        <el-option label="含抑制" value="all" />
      </el-select>
      <el-select v-model="query.severity" placeholder="级别" clearable style="width: 110px" @change="search">
        <el-option v-for="(l, k) in SEVERITY_LABEL" :key="k" :label="l" :value="k" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="告警名 / 摘要 / 资产 / 实例" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
      <el-tag v-if="query.assetNo" closable @close="(query.assetNo = ''), search()">资产 {{ query.assetNo }}</el-tag>
      <el-button type="primary" plain @click="search">查询</el-button>
      <div style="flex: 1"></div>
      <el-button v-if="canWrite" :disabled="!selected.length" @click="doAckBatch">批量确认（{{ selected.length }}）</el-button>
      <el-button :icon="'Refresh'" @click="refresh">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe row-key="id" @selection-change="(v: AlertItem[]) => (selected = v)">
      <el-table-column v-if="canWrite" type="selection" width="40" :selectable="(r: AlertItem) => r.status === 'FIRING' && !r.ackBy" />
      <el-table-column label="级别" width="70">
        <template #default="{ row }">
          <el-tag :type="SEVERITY_TAG[row.severity] || 'info'" size="small" effect="dark">{{ SEVERITY_LABEL[row.severity] || row.severity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="告警" min-width="240">
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row.id)">{{ row.alertname }}</el-link>
          <el-tag v-if="row.fireCount > 1" size="small" type="warning" style="margin-left: 6px">7 天内第 {{ row.fireCount }} 次</el-tag>
          <div class="sub">{{ row.summary }}</div>
        </template>
      </el-table-column>
      <el-table-column label="资产 / 实例" min-width="170">
        <template #default="{ row }">
          <template v-if="row.assetNo">
            <el-link @click="router.push({ path: '/monitoring/assets', query: { assetNo: row.assetNo } })">{{ row.assetNo }}</el-link>
            <div class="sub">{{ row.labels?.asset_name }} {{ row.rackCode ? '· ' + row.rackCode : '' }}</div>
          </template>
          <span v-else class="sub">{{ row.instance || '未关联资产' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="开始 / 持续" width="165">
        <template #default="{ row }">
          {{ fmt(row.startsAt) }}
          <div class="sub">{{ row.status === 'FIRING' ? '已持续' : '历时' }} {{ duration(row) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'RESOLVED'" type="success" size="small">已恢复</el-tag>
          <el-tooltip v-else-if="row.suppressed" :content="row.suppressReason" placement="top">
            <el-tag type="info" size="small">维护抑制</el-tag>
          </el-tooltip>
          <el-tag v-else type="danger" size="small" effect="plain">触发中</el-tag>
          <el-tag v-if="row.ackBy" size="small" style="margin-left: 4px">{{ row.ackBy }} 已确认</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="工单" width="165">
        <template #default="{ row }">
          <el-link v-if="row.woNo" type="primary" @click="router.push(`/workorders/${row.woNo}`)">{{ row.woNo }}</el-link>
          <span v-else class="sub">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'FIRING'">
            <el-button v-if="canWrite && !row.ackBy" link type="primary" @click="doAck(row)">确认</el-button>
            <el-button v-if="canTicket && !row.woNo" link type="primary" @click="doTicket(row)">转工单</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="page-pagination">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="search"
      />
    </div>

    <el-drawer v-model="drawer" size="640px" :title="detail ? `#${detail.alert.id} ${detail.alert.alertname}` : '告警详情'">
      <template v-if="detail">
        <div class="actions">
          <el-button v-if="canWrite && detail.alert.status === 'FIRING' && !detail.alert.ackBy" @click="doAck(detail.alert)">确认</el-button>
          <el-button v-if="canTicket && !detail.alert.woNo" type="primary" @click="doTicket(detail.alert)">转工单</el-button>
          <el-button v-if="detail.alert.woNo" type="primary" plain @click="router.push(`/workorders/${detail.alert.woNo}`)">查看工单 {{ detail.alert.woNo }}</el-button>
          <el-button v-if="detail.alert.assetNo" @click="router.push({ path: '/monitoring/assets', query: { assetNo: detail.alert.assetNo } })">资产指标</el-button>
          <el-button v-if="detail.alert.assetNo" @click="router.push({ path: '/ci-relations', query: { type: 'ASSET', id: detail.alert.assetNo } })">影响分析</el-button>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="级别">
            <el-tag :type="SEVERITY_TAG[detail.alert.severity] || 'info'" size="small">{{ SEVERITY_LABEL[detail.alert.severity] || detail.alert.severity }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.alert.status === 'FIRING' ? '触发中' : '已恢复' }}</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ detail.alert.summary }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.alert.description" label="详情" :span="2">
            <div style="white-space: pre-wrap">{{ detail.alert.description }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="资产">{{ detail.alert.assetNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实例">{{ detail.alert.instance || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开始">{{ fmt(detail.alert.startsAt) }}</el-descriptions-item>
          <el-descriptions-item label="结束">{{ fmt(detail.alert.endsAt) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ detail.alert.source }}</el-descriptions-item>
          <el-descriptions-item label="最近收到">{{ fmt(detail.alert.lastReceivedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.alert.suppressed" label="抑制原因" :span="2">{{ detail.alert.suppressReason }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.alert.ackBy" label="确认" :span="2">
            {{ detail.alert.ackBy }} · {{ fmt(detail.alert.ackAt) }} {{ detail.alert.ackNote ? '· ' + detail.alert.ackNote : '' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.alert.closeReason" label="关闭说明" :span="2">{{ detail.alert.closeReason }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="kbs.length">
          <h4 class="sec">处理参考（知识库）</h4>
          <div v-for="k in kbs" :key="k.id" class="kb">
            <el-tag size="small" :type="k.category === 'KNOWN_ERROR' ? 'danger' : 'primary'" effect="plain">{{ KB_CATEGORY_LABEL[k.category] }}</el-tag>
            <el-link type="primary" @click="router.push({ path: '/knowledge', query: { kb: k.kbNo } })">{{ k.title }}</el-link>
          </div>
        </template>
        <div v-if="detail.alert.assetNo" style="margin-top: 8px">
          <el-link type="warning" @click="router.push({ path: '/problems', query: { asset: detail.alert.assetNo } })">该资产反复出问题？到问题管理查看 / 登记问题 →</el-link>
        </div>

        <h4 class="sec">标签</h4>
        <div class="labels">
          <el-tag v-for="l in labelRows" :key="l.k" size="small" effect="plain">{{ l.k }}={{ l.v }}</el-tag>
        </div>

        <h4 class="sec">历史发作（同一告警）</h4>
        <el-table :data="detail.history" size="small" border>
          <el-table-column label="开始" width="160"><template #default="{ row }">{{ fmt(row.startsAt) }}</template></el-table-column>
          <el-table-column label="结束" width="160"><template #default="{ row }">{{ fmt(row.endsAt) }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="woNo" label="工单" />
        </el-table>

        <template v-if="detail.related?.length">
          <h4 class="sec">同资产近 7 天其他告警</h4>
          <el-table :data="detail.related" size="small" border>
            <el-table-column prop="alertname" label="告警" />
            <el-table-column label="级别" width="70"><template #default="{ row }">{{ SEVERITY_LABEL[row.severity] || row.severity }}</template></el-table-column>
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column label="开始" width="160"><template #default="{ row }">{{ fmt(row.startsAt) }}</template></el-table-column>
          </el-table>
        </template>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px 14px;
  cursor: pointer;
}
.card:hover {
  border-color: var(--el-color-primary);
}
.card.static {
  cursor: default;
}
.card.static:hover {
  border-color: var(--el-border-color-lighter);
}
.num {
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.num small {
  font-size: 12px;
  font-weight: 400;
}
.c-danger {
  color: var(--el-color-danger);
}
.c-warning {
  color: var(--el-color-warning);
}
.c-info {
  color: var(--el-text-color-secondary);
}
.lbl {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.actions .el-button + .el-button {
  margin-left: 0;
}
.sec {
  margin: 16px 0 8px;
  font-size: 14px;
}
.kb {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 4px 0;
}
.labels {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
