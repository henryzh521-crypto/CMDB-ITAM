<script setup lang="ts">
/**
 * 资产监控：按资产查看 Prometheus 标准指标（存活/延迟、CPU、内存、磁盘、负载、网络、GPU、功率、进风温度）。
 * 资产没有对应采集器的面板自动隐藏；同时展示该资产当前告警。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import echarts, { CHART_COLORS, type EChartsType } from '@/utils/echarts'
import AssetPicker from '@/components/AssetPicker.vue'
import { SEVERITY_LABEL, SEVERITY_TAG, fetchAlerts, fetchAssetMetrics } from '@/api/monitoring'
import type { AlertItem, AssetMetrics } from '@/api/monitoring'

const route = useRoute()
const router = useRouter()

interface PanelDef {
  key: string
  title: string
  unit: string
  max?: number
  fmt?: (v: number) => string
}

function bits(v: number) {
  const u = ['bps', 'Kbps', 'Mbps', 'Gbps', 'Tbps']
  let i = 0
  while (Math.abs(v) >= 1000 && i < u.length - 1) {
    v /= 1000
    i++
  }
  return `${v.toFixed(v >= 100 ? 0 : 1)} ${u[i]}`
}

const PANELS: PanelDef[] = [
  { key: 'ping', title: 'Ping 存活', unit: '', max: 1, fmt: (v) => (v >= 1 ? '在线' : '离线') },
  { key: 'pingRtt', title: 'Ping 延迟', unit: 'ms' },
  { key: 'cpu', title: 'CPU 使用率', unit: '%', max: 100 },
  { key: 'mem', title: '内存使用率', unit: '%', max: 100 },
  { key: 'disk', title: '磁盘使用率（最高分区）', unit: '%', max: 100 },
  { key: 'load', title: '5 分钟负载', unit: '' },
  { key: 'netIn', title: '网络入流量', unit: '', fmt: bits },
  { key: 'netOut', title: '网络出流量', unit: '', fmt: bits },
  { key: 'gpuUtil', title: 'GPU 平均利用率', unit: '%', max: 100 },
  { key: 'gpuTemp', title: 'GPU 最高温度', unit: '℃' },
  { key: 'gpuMem', title: 'GPU 显存使用率', unit: '%', max: 100 },
  { key: 'power', title: '整机功率', unit: 'W' },
  { key: 'inletTemp', title: '进风温度', unit: '℃' },
]

const assetNo = ref<string | undefined>(typeof route.query.assetNo === 'string' ? route.query.assetNo : undefined)
const hours = ref(6)
const data = ref<AssetMetrics | null>(null)
const alerts = ref<AlertItem[]>([])
const loading = ref(false)
const charts = new Map<string, EChartsType>()
const els = ref<Record<string, HTMLDivElement>>({})

const visible = computed(() => PANELS.filter((p) => data.value?.series[p.key]?.length))

function fmtVal(p: PanelDef, v: unknown) {
  const n = typeof v === 'number' ? v : Number((v as any)?.value ?? v)
  if (!Number.isFinite(n)) return '-'
  if (p.fmt) return p.fmt(n)
  return `${n.toFixed(n >= 100 ? 0 : 1)}${p.unit ? ' ' + p.unit : ''}`
}

function setEl(key: string, el: unknown) {
  if (el) els.value[key] = el as HTMLDivElement
}

function render() {
  for (const p of visible.value) {
    const el = els.value[p.key]
    if (!el) continue
    let c = charts.get(p.key)
    if (!c) {
      c = echarts.init(el)
      charts.set(p.key, c)
    }
    const ss = data.value!.series[p.key]
    c.setOption(
      {
        color: CHART_COLORS,
        tooltip: {
          trigger: 'axis',
          valueFormatter: (v: number) => (v == null ? '-' : fmtVal(p, v)),
        },
        grid: { left: 56, right: 12, top: 12, bottom: 24 },
        xAxis: { type: 'time' },
        yAxis: {
          type: 'value',
          max: p.max,
          min: p.max ? 0 : undefined,
          interval: p.key === 'ping' ? 1 : undefined,
          axisLabel: {
            formatter: (v: number) => (p.key === 'ping' ? (v >= 1 ? '在线' : '离线') : p.fmt ? p.fmt(v) : String(v)),
          },
        },
        series: ss.map((s) => ({
          name: s.metric.instance || s.metric.gpu || s.metric.asset_no || p.title,
          type: 'line',
          showSymbol: false,
          step: p.key === 'ping' ? 'end' : undefined,
          areaStyle: ss.length === 1 ? { opacity: 0.08 } : undefined,
          data: s.points.map(([t, v]) => [t * 1000, v]),
        })),
      },
      true,
    )
  }
}

async function load() {
  if (!assetNo.value) return
  loading.value = true
  try {
    const [m, a] = await Promise.all([
      fetchAssetMetrics(assetNo.value, hours.value),
      fetchAlerts({ page: 1, size: 20, assetNo: assetNo.value, status: 'FIRING' }),
    ])
    data.value = m
    alerts.value = a.records ?? []
    await nextTick()
    render()
  } catch {
    data.value = null
  } finally {
    loading.value = false
  }
}

watch(assetNo, (v) => {
  router.replace({ query: v ? { assetNo: v } : {} })
  charts.forEach((c) => c.dispose())
  charts.clear()
  data.value = null
  void load()
})

function onResize() {
  charts.forEach((c) => c.resize())
}

const upText = computed(() => {
  const up = data.value?.up || []
  if (!up.length) return null
  return up.map((u) => ({ job: u.metric.job, instance: u.metric.instance, ok: Number(u.value) === 1 }))
})

onMounted(() => {
  window.addEventListener('resize', onResize)
  void load()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
  charts.clear()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">资产监控</div>
    <div class="page-toolbar">
      <AssetPicker v-model="assetNo as any" style="width: 320px" />
      <el-radio-group v-model="hours" @change="load">
        <el-radio-button :value="1">1 小时</el-radio-button>
        <el-radio-button :value="6">6 小时</el-radio-button>
        <el-radio-button :value="24">24 小时</el-radio-button>
        <el-radio-button :value="168">7 天</el-radio-button>
      </el-radio-group>
      <el-button :icon="'Refresh'" :disabled="!assetNo" @click="load">刷新</el-button>
      <div style="flex: 1"></div>
      <el-button v-if="assetNo" @click="router.push({ path: '/monitoring/alerts', query: { assetNo } })">告警历史</el-button>
      <el-button v-if="assetNo" @click="router.push({ path: '/ci-relations', query: { type: 'ASSET', id: assetNo } })">影响分析</el-button>
    </div>

    <el-empty v-if="!assetNo" description="选择一个资产查看监控指标" />
    <div v-else v-loading="loading">
      <div v-if="upText" class="ups">
        <el-tag v-for="u in upText" :key="u.job + u.instance" :type="u.ok ? 'success' : 'danger'" effect="plain">
          {{ u.job }} · {{ u.instance }} · {{ u.ok ? '采集正常' : '采集失败' }}
        </el-tag>
      </div>

      <el-alert
        v-for="a in alerts"
        :key="a.id"
        :type="SEVERITY_TAG[a.severity] === 'danger' ? 'error' : SEVERITY_TAG[a.severity] || 'info'"
        :closable="false"
        show-icon
        style="margin-bottom: 8px"
      >
        <template #title>
          [{{ SEVERITY_LABEL[a.severity] || a.severity }}] {{ a.alertname }} — {{ a.summary }}
          <span v-if="a.woNo">（工单 {{ a.woNo }}）</span>
          <span v-if="a.suppressed">（维护抑制）</span>
        </template>
      </el-alert>

      <el-empty
        v-if="data && !visible.length"
        description="暂无该资产的监控数据：请确认资产已填写 IP、状态在监控范围内，且对应采集器（node_exporter / DCGM / IPMI 等）已部署"
      />

      <div class="grid">
        <div v-for="p in visible" :key="p.key" class="panel">
          <div class="ph">
            <span>{{ p.title }}</span>
            <b>{{ data?.current[p.key] !== undefined && !Array.isArray(data?.current[p.key]) ? fmtVal(p, data?.current[p.key]) : '' }}</b>
          </div>
          <div :ref="(el) => setEl(p.key, el)" class="chart"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ups {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 12px;
}
.panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px 12px 4px;
}
.ph {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}
.ph b {
  font-variant-numeric: tabular-nums;
}
.chart {
  height: 180px;
}
@media (max-width: 480px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
