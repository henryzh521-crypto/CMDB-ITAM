<script setup lang="ts">
/**
 * 资产生命周期
 * - 概览：近 N 天活动统计、业务状态分布、90 天内过保数量
 * - 活动流：全部资产的生命周期事件（新增/上下架/调拨/状态流转/部件更换…）
 * - 单资产时间线：选择资产后查看完整留痕，并可跳转 CI 影响分析
 */
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/api/client'
import type { PageResult } from '@/api/types'
import { fetchAssetDetail, fetchAssetTimeline } from '@/api/asset'
import type { AssetDetail, AssetTimelineItem } from '@/api/asset'
import AssetPicker from '@/components/AssetPicker.vue'
import echarts, { CHART_COLORS } from '@/utils/echarts'
import type { EChartsType } from '@/utils/echarts'
import { escapeHtml } from '@/utils/escape'
import { ASSET_EVENT_LABEL, ASSET_EVENT_TAG, assetEventLabel, TIMELINE_SOURCE_LABEL, TIMELINE_SOURCE_TAG } from '@/utils/assetEvents'

interface Activity {
  id: number
  assetNo: string
  assetName?: string
  statusBiz?: string
  deleted: boolean
  eventType: string
  operator?: string
  refDoc?: string
  eventTime: string
}

interface Stats {
  byType: { key: string; count: number }[]
  daily: { day: string; count: number }[]
  byStatus: { key: string; count: number }[]
  warrantyExpiring: number
}

const router = useRouter()
const days = ref(30)
const stats = ref<Stats | null>(null)
const trendEl = ref<HTMLDivElement>()
const statusEl = ref<HTMLDivElement>()
let trendChart: EChartsType | null = null
let statusChart: EChartsType | null = null

const q = reactive({ page: 1, size: 20, eventType: '', operator: '' })
const activities = ref<Activity[]>([])
const total = ref(0)
const loading = ref(false)

const assetNo = ref('')
const asset = ref<AssetDetail | null>(null)
const timeline = ref<AssetTimelineItem[]>([])
const tlTotal = ref(0)
const tlPage = ref(1)
const tlLoading = ref(false)
/** v3.6：时间线来源过滤（空 = 全部：台账/工单/财务/告警） */
const tlSource = ref('')

function fmt(v?: string) {
  return v ? v.replace('T', ' ').slice(0, 19) : '-'
}

async function loadStats() {
  try {
    stats.value = await http.get<any, Stats>('/asset-lifecycle/stats', { params: { days: days.value } })
    await nextTick()
    renderCharts()
  } catch {
    // ignore
  }
}

async function loadActivities() {
  loading.value = true
  try {
    const res = await http.get<any, PageResult<Activity>>('/asset-lifecycle/activities', {
      params: { page: q.page, size: q.size, days: days.value, eventType: q.eventType || undefined, operator: q.operator || undefined },
    })
    activities.value = res.records ?? []
    total.value = res.total ?? 0
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  const s = stats.value
  if (!s) return
  if (trendEl.value) {
    trendChart = trendChart || echarts.init(trendEl.value)
    trendChart.setOption({
      color: CHART_COLORS,
      grid: { left: 36, right: 12, top: 16, bottom: 24 },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: s.daily.map((d) => d.day.slice(5)) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{ type: 'bar', name: '事件数', data: s.daily.map((d) => d.count), barMaxWidth: 18 }],
    })
  }
  if (statusEl.value) {
    statusChart = statusChart || echarts.init(statusEl.value)
    statusChart.setOption({
      color: CHART_COLORS,
      tooltip: { trigger: 'item', formatter: (p: any) => `${escapeHtml(p.name)}：${p.value} 台（${p.percent}%）` },
      series: [
        {
          type: 'pie',
          radius: ['45%', '72%'],
          label: { formatter: '{b}\n{c}' },
          data: s.byStatus.map((x) => ({ name: x.key, value: x.count })),
        },
      ],
    })
  }
}

async function selectAsset(no: string) {
  assetNo.value = no
  tlPage.value = 1
  if (!no) {
    asset.value = null
    timeline.value = []
    return
  }
  try {
    asset.value = await fetchAssetDetail(no)
  } catch {
    asset.value = null
  }
  await loadTimeline()
}

async function loadTimeline() {
  if (!assetNo.value) return
  tlLoading.value = true
  try {
    const res = await fetchAssetTimeline(assetNo.value, { page: tlPage.value, size: 20, source: tlSource.value || undefined })
    timeline.value = res.records ?? []
    tlTotal.value = res.total ?? 0
  } catch {
    timeline.value = []
  } finally {
    tlLoading.value = false
  }
}

/** v3.6：时间线条目摘要文案（台账源沿用快照摘要，工单/财务/告警源解析 detail JSON） */
function itemText(ev: AssetTimelineItem): string {
  if (ev.source === 'asset') {
    const s = ev.detail ? (JSON.parse(ev.detail) as Record<string, any>) : null
    if (!s || typeof s !== 'object') return ''
    if (ev.eventType === 'STATUS_CHANGE' && s.before && s.after) {
      return `${s.before.statusBiz ?? '-'} → ${s.after.statusBiz ?? '-'}${s.reason ? '（' + s.reason + '）' : ''}`
    }
    if ((ev.eventType === 'RACKED' || ev.eventType === 'MOVED') && s.after) {
      return `${s.after.rackCode ?? ''} ${s.after.unitNo ? 'U' + s.after.unitNo : ''}`.trim()
    }
    if (ev.eventType === 'UNRACKED' && s.before) {
      return `自 ${s.before.rackCode ?? ''} ${s.before.unitNo ? 'U' + s.before.unitNo : ''}`.trim()
    }
    return ''
  }
  let d: Record<string, any> = {}
  try {
    d = ev.detail ? JSON.parse(ev.detail) : {}
  } catch {
    return ''
  }
  if (ev.eventType === 'WO_SUBMIT' || ev.eventType === 'WO_FINISH') {
    return `${d.woType ?? '-'}《${d.title ?? '-'}》　状态：${d.status ?? '-'}${d.priority ? '　优先级：' + d.priority : ''}`
  }
  if (ev.eventType === 'DEPRECIATION') {
    return `${d.period ?? '-'}　当月折旧 ${d.monthDep ?? '-'}　累计 ${d.accumulated ?? '-'}　净值 ${d.netValue ?? '-'}`
  }
  if (ev.eventType === 'ALERT_FIRING' || ev.eventType === 'ALERT_RESOLVED') {
    return `${d.alertname ?? '-'}（${d.severity ?? '-'}）${d.summary ? '　' + d.summary : ''}`
  }
  return ''
}

function onResize() {
  trendChart?.resize()
  statusChart?.resize()
}

watch(days, () => {
  q.page = 1
  void loadStats()
  void loadActivities()
})

onMounted(() => {
  window.addEventListener('resize', onResize)
  void loadStats()
  void loadActivities()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose()
  statusChart?.dispose()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">资产生命周期</div>

    <div class="page-toolbar">
      <div style="width: 360px">
        <AssetPicker
          :model-value="assetNo"
          placeholder="查看单台资产的完整生命周期"
          @update:model-value="(v) => selectAsset(Array.isArray(v) ? v[0] || '' : v || '')"
        />
      </div>
      <div style="flex: 1"></div>
      <el-radio-group v-model="days" size="small">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="90">近 90 天</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 单资产时间线 -->
    <el-card v-if="asset" shadow="never" class="asset-card">
      <template #header>
        <div class="asset-head">
          <span><b>{{ asset.assetNo }}</b>　{{ asset.name }}</span>
          <span>
            <el-tag size="small">{{ asset.statusBiz }}</el-tag>
            <el-tag size="small" type="info" style="margin-left: 6px">{{ asset.statusFin }}</el-tag>
            <el-button link type="primary" style="margin-left: 12px" @click="router.push({ path: '/ci-relations', query: { type: 'ASSET', id: asset.assetNo } })">
              影响分析
            </el-button>
            <el-button link @click="selectAsset('')">关闭</el-button>
          </span>
        </div>
      </template>
      <el-descriptions :column="4" size="small" border>
        <el-descriptions-item label="位置">{{ asset.rackCode ? `${asset.roomCode || ''} / ${asset.rackCode} U${asset.unitNo ?? ''}` : '未上架' }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ asset.owner || '-' }}</el-descriptions-item>
        <el-descriptions-item label="采购日期">{{ asset.purchaseDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="保修截止">{{ asset.warrantyEnd || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="tl-bar">
        <b>全生命周期时间线</b>
        <el-select v-model="tlSource" size="small" style="width: 130px" @change="(tlPage = 1), loadTimeline()">
          <el-option label="全部来源" value="" />
          <el-option v-for="(l, k) in TIMELINE_SOURCE_LABEL" :key="k" :label="l" :value="k" />
        </el-select>
      </div>
      <div v-loading="tlLoading" class="timeline">
        <el-timeline v-if="timeline.length">
          <el-timeline-item
            v-for="ev in timeline"
            :key="`${ev.source}-${ev.eventType}-${ev.refDoc ?? ev.id ?? ''}`"
            :timestamp="fmt(ev.eventTime)"
            placement="top"
            :type="ASSET_EVENT_TAG[ev.eventType] || 'primary'"
          >
            <b>{{ assetEventLabel(ev.eventType) }}</b>
            <el-tag size="small" :type="TIMELINE_SOURCE_TAG[ev.source] || 'info'" style="margin-left: 6px">{{ TIMELINE_SOURCE_LABEL[ev.source] || ev.source }}</el-tag>
            <span class="sub">　{{ itemText(ev) }}</span>
            <div class="sub">操作人：{{ ev.operator || '-' }}<template v-if="ev.refDoc">　单据：{{ ev.refDoc }}</template></div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else :image-size="50" description="暂无生命周期记录" />
        <el-pagination
          v-if="tlTotal > 20"
          v-model:current-page="tlPage"
          :page-size="20"
          :total="tlTotal"
          layout="prev, pager, next"
          small
          @current-change="loadTimeline"
        />
      </div>
    </el-card>

    <!-- 概览 -->
    <el-row :gutter="16" class="overview">
      <el-col :span="5">
        <div class="kpi">
          <div class="kpi-num">{{ stats?.byType.reduce((s, x) => s + x.count, 0) ?? 0 }}</div>
          <div class="kpi-lbl">近 {{ days }} 天生命周期事件</div>
        </div>
        <div class="kpi">
          <div class="kpi-num warn">{{ stats?.warrantyExpiring ?? 0 }}</div>
          <div class="kpi-lbl">90 天内过保资产</div>
        </div>
        <div class="types">
          <div v-for="t in stats?.byType || []" :key="t.key" class="type-row">
            <el-tag size="small" :type="ASSET_EVENT_TAG[t.key] || 'info'">{{ assetEventLabel(t.key) }}</el-tag>
            <span>{{ t.count }}</span>
          </div>
        </div>
      </el-col>
      <el-col :span="11">
        <div class="chart-title">每日事件</div>
        <div ref="trendEl" class="chart"></div>
      </el-col>
      <el-col :span="8">
        <div class="chart-title">业务状态分布</div>
        <div ref="statusEl" class="chart"></div>
      </el-col>
    </el-row>

    <!-- 活动流 -->
    <div class="page-toolbar" style="margin-top: 8px">
      <b>活动流</b>
      <el-select v-model="q.eventType" placeholder="事件类型" clearable style="width: 140px" @change="(q.page = 1), loadActivities()">
        <el-option v-for="(l, k) in ASSET_EVENT_LABEL" :key="k" :label="l" :value="k" />
      </el-select>
      <el-input v-model="q.operator" placeholder="操作人用户名" clearable style="width: 160px" @keyup.enter="(q.page = 1), loadActivities()" @clear="loadActivities" />
    </div>
    <el-table v-loading="loading" :data="activities" border stripe size="small" @row-click="(r: Activity) => !r.deleted && selectAsset(r.assetNo)">
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ fmt(row.eventTime) }}</template>
      </el-table-column>
      <el-table-column label="事件" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="ASSET_EVENT_TAG[row.eventType] || 'info'">{{ assetEventLabel(row.eventType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="assetNo" label="资产编号" width="200" />
      <el-table-column label="名称" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.assetName || '-' }}<el-tag v-if="row.deleted" size="small" type="info" style="margin-left: 6px">已删除</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="statusBiz" label="当前状态" width="110" />
      <el-table-column prop="operator" label="操作人" width="110" />
      <el-table-column prop="refDoc" label="关联单据" width="160" show-overflow-tooltip />
    </el-table>
    <div class="page-pagination">
      <el-pagination
        v-model:current-page="q.page"
        :page-size="q.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadActivities"
      />
    </div>
  </div>
</template>

<style scoped>
.asset-card {
  margin-bottom: 16px;
}
.asset-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.timeline {
  margin-top: 14px;
  max-height: 420px;
  overflow: auto;
  padding-right: 8px;
}
.tl-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}
.sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.overview {
  margin-bottom: 8px;
}
.kpi {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.kpi-num {
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.kpi-num.warn {
  color: var(--el-color-warning);
}
.kpi-lbl {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.types {
  max-height: 150px;
  overflow: auto;
}
.type-row {
  display: flex;
  justify-content: space-between;
  padding: 3px 0;
  font-size: 13px;
}
.chart-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.chart {
  height: 260px;
}
</style>
