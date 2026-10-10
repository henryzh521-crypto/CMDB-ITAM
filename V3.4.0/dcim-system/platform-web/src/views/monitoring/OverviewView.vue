<script setup lang="ts">
/**
 * 监控概览：Prometheus 连通性、各采集任务健康（CMDB 目标数 vs 实际在线）、
 * 近 7 天告警趋势、告警最多的资产 / 规则。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import echarts, { type EChartsType } from '@/utils/echarts'
import { JOB_LABEL, SEVERITY_LABEL, fetchAlertStats, fetchMonitoringOverview } from '@/api/monitoring'
import type { AlertStats, MonitoringOverview } from '@/api/monitoring'

const router = useRouter()
const overview = ref<MonitoringOverview | null>(null)
const stats = ref<AlertStats | null>(null)
const loading = ref(false)
const trendEl = ref<HTMLDivElement>()
let trend: EChartsType | null = null

const jobRows = computed(() =>
  Object.entries(overview.value?.jobs || {}).map(([job, h]) => ({
    job,
    label: JOB_LABEL[job] || job,
    cmdbTargets: h.cmdbTargets,
    up: h.up ?? 0,
    down: h.down ?? 0,
    total: h.total ?? 0,
    errors: h.errors || [],
    rate: h.total ? Math.round(((h.up ?? 0) / h.total) * 1000) / 10 : null,
  })),
)

const totalDown = computed(() => jobRows.value.reduce((s, r) => s + r.down, 0))
const firingTotal = computed(() =>
  Object.values(stats.value?.firingBySeverity || {}).reduce((s, n) => s + n, 0),
)

function renderTrend() {
  if (!trendEl.value || !stats.value) return
  const days: string[] = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date(Date.now() - i * 86400000)
    days.push(`${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`)
  }
  const sevs = ['critical', 'warning', 'info']
  const colors: Record<string, string> = { critical: '#f56c6c', warning: '#e6a23c', info: '#909399' }
  const series = sevs.map((s) => ({
    name: SEVERITY_LABEL[s],
    type: 'bar' as const,
    stack: 'all',
    itemStyle: { color: colors[s] },
    data: days.map((d) => {
      const r = stats.value!.daily.find((x) => x.day === d && x.severity === s)
      return r ? Number(r.n) : 0
    }),
  }))
  trend ??= echarts.init(trendEl.value)
  trend.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 40, right: 16, top: 32, bottom: 24 },
    xAxis: { type: 'category', data: days },
    yAxis: { type: 'value', minInterval: 1 },
    series,
  })
}

async function load() {
  loading.value = true
  try {
    const [o, s] = await Promise.all([fetchMonitoringOverview(), fetchAlertStats()])
    overview.value = o
    stats.value = s
    await nextTick()
    renderTrend()
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

function onResize() {
  trend?.resize()
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  void load()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trend?.dispose()
  trend = null
})
</script>

<template>
  <div class="page-card" v-loading="loading">
    <div class="page-title">
      监控概览
      <el-button style="float: right" :icon="'Refresh'" @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="overview && !overview.prometheusConfigured"
      type="warning"
      :closable="false"
      show-icon
      title="尚未配置 Prometheus 地址"
      description="告警接入不受影响；配置后可查看采集健康与资产实时指标。请在「监控设置」中填写 Prometheus 地址（Docker 部署默认 http://prometheus:9090）。"
      style="margin-bottom: 16px"
    />
    <el-alert
      v-else-if="overview && overview.prometheusUp === false"
      type="error"
      :closable="false"
      show-icon
      title="Prometheus 无法访问"
      :description="overview.prometheusError"
      style="margin-bottom: 16px"
    />

    <div class="cards">
      <div class="card">
        <div class="num">{{ overview?.monitoredAssets ?? '-' }}</div>
        <div class="lbl">纳管资产（有 IP 且状态在监控范围）</div>
      </div>
      <div class="card link" @click="router.push('/monitoring/alerts')">
        <div class="num" :class="{ 'c-danger': firingTotal > 0 }">{{ firingTotal }}</div>
        <div class="lbl">活动告警（不含维护抑制）</div>
      </div>
      <div class="card">
        <div class="num" :class="{ 'c-danger': totalDown > 0 }">{{ totalDown }}</div>
        <div class="lbl">采集失败目标</div>
      </div>
      <div class="card">
        <div class="num">{{ stats?.mttrMinutes ?? 0 }}<small> 分钟</small></div>
        <div class="lbl">平均恢复时长（30 天）</div>
      </div>
    </div>

    <h4 class="sec">采集任务健康</h4>
    <el-table :data="jobRows" border size="small">
      <el-table-column type="expand" width="36">
        <template #default="{ row }">
          <div v-if="!row.errors.length" class="sub" style="padding: 8px 16px">无采集错误</div>
          <el-table v-else :data="row.errors" size="small" style="margin: 0 16px; width: auto">
            <el-table-column prop="instance" label="实例" width="200" />
            <el-table-column prop="assetNo" label="资产" width="160" />
            <el-table-column prop="error" label="错误" />
          </el-table>
        </template>
      </el-table-column>
      <el-table-column label="任务" min-width="160">
        <template #default="{ row }">
          {{ row.label }} <span class="sub">{{ row.job }}</span>
        </template>
      </el-table-column>
      <el-table-column label="CMDB 下发目标" width="130" align="right">
        <template #default="{ row }">{{ row.cmdbTargets ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="total" label="Prometheus 目标" width="140" align="right" />
      <el-table-column label="在线" width="90" align="right">
        <template #default="{ row }"><span class="c-success">{{ row.up }}</span></template>
      </el-table-column>
      <el-table-column label="离线" width="90" align="right">
        <template #default="{ row }"><span :class="{ 'c-danger': row.down > 0 }">{{ row.down }}</span></template>
      </el-table-column>
      <el-table-column label="在线率" width="200">
        <template #default="{ row }">
          <el-progress
            v-if="row.rate !== null"
            :percentage="row.rate"
            :status="row.rate >= 99 ? 'success' : row.rate >= 90 ? 'warning' : 'exception'"
            :stroke-width="10"
          />
          <span v-else class="sub">未采集</span>
        </template>
      </el-table-column>
    </el-table>
    <div class="sub" style="margin-top: 6px">
      「CMDB 下发目标」与「Prometheus 目标」不一致时，通常是 Prometheus 尚未刷新（默认 1 分钟）或该任务未在 prometheus.yml 中启用。
    </div>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :lg="12">
        <h4 class="sec">近 7 天告警趋势</h4>
        <div ref="trendEl" style="height: 260px"></div>
      </el-col>
      <el-col :xs="24" :lg="6">
        <h4 class="sec">告警最多的资产（7 天）</h4>
        <el-table :data="stats?.topAssets || []" size="small" :show-header="false">
          <el-table-column>
            <template #default="{ row }">
              <el-link @click="router.push({ path: '/monitoring/alerts', query: { assetNo: row.assetNo } })">{{ row.assetNo }}</el-link>
              <div class="sub">{{ row.assetName }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="n" width="60" align="right" />
        </el-table>
      </el-col>
      <el-col :xs="24" :lg="6">
        <h4 class="sec">最频繁的告警（7 天）</h4>
        <el-table :data="stats?.topAlerts || []" size="small" :show-header="false">
          <el-table-column prop="alertname" />
          <el-table-column prop="n" width="60" align="right" />
        </el-table>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 8px;
}
.card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px 14px;
}
.card.link {
  cursor: pointer;
}
.card.link:hover {
  border-color: var(--el-color-primary);
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
.lbl,
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.c-danger {
  color: var(--el-color-danger);
}
.c-success {
  color: var(--el-color-success);
}
.sec {
  margin: 16px 0 8px;
  font-size: 14px;
}
</style>
