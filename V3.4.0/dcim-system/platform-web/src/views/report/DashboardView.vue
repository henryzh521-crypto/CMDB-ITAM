<script setup lang="ts">
import { ASSET_EVENT_LABEL } from '@/utils/assetEvents'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import echarts, { CHART_COLORS, axisColor, surfaceColor, type EChartsType } from '@/utils/echarts'
import { escapeHtml } from '@/utils/escape'
import { fetchDashboard, fetchWorkorderSla, fetchRepairCost, fetchRepairDuration, fetchAssetFinanceSummary, fetchOpexStructure, fetchSupplierPerformance, fetchCapacityPlanning, fetchAssetTco } from '@/api/report'
import type { CategoryStatusItem, DashboardData, GroupCountItem, RoomUsageItem, AssetFinanceSummary, OpexStructureData, AssetRoiData, SupplierPerformanceData, CapacityPlanningData, AssetTcoData } from '@/api/report'

/* ---------------- 数据加载 ---------------- */
const auth = useAuthStore()
/** v3.4：运营总览依赖「报表」读权限；没有时不发请求（以前会连弹多条「没有执行该操作的权限」） */
const canSeeReport = computed(() => auth.canRead('report'))
const loading = ref(false)
const data = ref<DashboardData | null>(null)
const slaData = ref<any>(null)
const repairCostData = ref<any>(null)
const repairDurationData = ref<any>(null)
const assetFinanceData = ref<AssetFinanceSummary | null>(null)
const opexData = ref<OpexStructureData | null>(null)
const roiData = ref<AssetRoiData | null>(null)
const supplierData = ref<SupplierPerformanceData | null>(null)
const capacityData = ref<CapacityPlanningData | null>(null)
const tcoData = ref<AssetTcoData | null>(null)

async function load() {
  loading.value = true
  try {
    // 单个接口失败（如后端未实现的报表接口）不拖垮整体：
    // 降级为 null，对应图表显示"暂无数据"，其余照常渲染。
    const safe = <T,>(p: Promise<T>): Promise<T | null> => p.catch(() => null)
    const [
      dash,
      sla,
      repairCost,
      repairDuration,
      finance,
      opex,
      roi,
      supplier,
      capacity,
      tco
    ] = await Promise.all([
      safe(fetchDashboard()),
      safe(fetchWorkorderSla()),
      safe(fetchRepairCost()),
      safe(fetchRepairDuration()),
      safe(fetchAssetFinanceSummary()),
      safe(fetchOpexStructure()),
      // ROI 依赖营收数据，平台不采集，后端不提供该接口（面板随之隐藏）
      Promise.resolve(null as AssetRoiData | null),
      safe(fetchSupplierPerformance()),
      safe(fetchCapacityPlanning()),
      safe(fetchAssetTco()),
    ])
    data.value = dash
    slaData.value = sla
    repairCostData.value = repairCost
    repairDurationData.value = repairDuration
    assetFinanceData.value = finance
    opexData.value = opex
    roiData.value = roi
    supplierData.value = supplier
    capacityData.value = capacity
    tcoData.value = tco
    await nextTick()
    renderCharts()
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

/* ---------------- 顶部统计卡 ---------------- */
function statusCount(keyword: string): number {
  return (
    data.value?.byStatusBiz.filter((s) => s.key.includes(keyword)).reduce((a, b) => a + b.count, 0) ?? 0
  )
}

function pendingTotal(): number {
  const wo = data.value?.woStats
  return (wo?.pending ?? 0) + (wo?.approving ?? 0)
}

function usagePercent(): string {
  return ((data.value?.rackUsage.usageRate ?? 0) * 100).toFixed(1)
}

/** 趋势末两月：本月 / 上月上架数（KPI 卡副文案） */
function rackedThisAndLastMonth(): string {
  const t = data.value?.trendRacked ?? []
  if (t.length < 2) return '暂无趋势数据'
  const last = t[t.length - 1]
  const prev = t[t.length - 2]
  return `本月 ${last.count} 台 · 上月 ${prev.count} 台`
}

/* ---------------- 图表定义（标题 + 副标题 + option） ---------------- */
type ChartKey =
  // 核心运营指标
  | 'status'
  | 'region'
  // 财务决策图表
  | 'assetValue'
  | 'assetRoi'
  | 'assetTco'
  | 'opexStructure'
  // 运维效率图表
  | 'repairCost'
  | 'slaTrend'
  | 'woEfficiency'
  // 供应链/风险图表
  | 'supplierPerformance'
  | 'expiryAlert'
  // 容量规划图表
  | 'capacityForecast'
  | 'capacityPlanning'

type ChartOption = Parameters<EChartsType['setOption']>[0]

interface ChartDef {
  key: ChartKey
  title: string
  extra: () => string
  option: () => ChartOption
}

/** 事件类型中英对照：与台账详情 / 生命周期页共用 utils/assetEvents（此前本页只登记了 6 种，上下架/调拨/换件显示英文码） */
const EVENT_LABELS = ASSET_EVENT_LABEL

const fmt = (n: number | null | undefined): string => (n ?? 0).toLocaleString('zh-CN')
const monthLabel = (key: string): string => `${key.slice(5)} 月`

/** 状态色：饼图与堆叠柱共用同一套索引，跨图颜色一致 */
function colorOf(key: string, order: string[]): string {
  const i = order.indexOf(key)
  return CHART_COLORS[(i < 0 ? 0 : i) % CHART_COLORS.length]
}

function emptyOption(title: string): ChartOption {
  return {
    title: {
      text: '暂无数据',
      left: 'center',
      top: 'middle',
      textStyle: { fontSize: 13, color: '#c0c4cc', fontWeight: 400 },
      subtext: title,
      subtextStyle: { fontSize: 11, color: '#c0c4cc' },
    },
  } as ChartOption
}

function donutOption(
  items: GroupCountItem[],
  title: string,
  unit: string,
  nameOf: (key: string) => string,
): ChartOption {
  if (items.length === 0) return emptyOption(title)
  return {
    title: { text: title, left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: {
      trigger: 'item',
      formatter: (p: { name: string; value: number; percent: number }) =>
        `${escapeHtml(p.name)}：${fmt(p.value)} ${unit}（${p.percent}%）`,
    },
    legend: { bottom: 0, type: 'scroll', icon: 'circle', itemWidth: 8, itemHeight: 8, itemGap: 12 },
    series: [
      {
        type: 'pie',
        radius: ['46%', '70%'],
        center: ['50%', '46%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 5, borderColor: surfaceColor(), borderWidth: 2 },
        label: { show: true, fontSize: 11, lineHeight: 15, formatter: (p: { name: string; value: number }) => `${p.name}\n${fmt(p.value)}` },
        labelLine: { length: 8, length2: 6 },
        data: items.map((it, i) => ({
          name: nameOf(it.key) || it.label || it.key,
          value: it.count,
          itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] },
        })),
      },
    ],
  } as ChartOption
}

function hBarOption(items: GroupCountItem[], title: string, unit: string): ChartOption {
  if (items.length === 0) return emptyOption(title)
  return {
    title: { text: title, left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (rows: { name: string; value: number }[]) =>
        `${escapeHtml(rows[0]?.name ?? '')}：${fmt(rows[0]?.value)} ${unit}`,
    },
    grid: { left: 8, right: 44, top: 40, bottom: 20, containLabel: true },
    xAxis: {
      type: 'value',
      name: unit,
      nameTextStyle: { color: '#909399' },
      splitLine: { lineStyle: { type: 'dashed', color: '#ebeef5' } },
    },
    yAxis: {
      type: 'category',
      data: items.map((it) => it.label || it.key),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: axisColor() },
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 16,
        data: items.map((it, i) => ({
          value: it.count,
          itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length], borderRadius: [0, 3, 3, 0] },
        })),
        label: { show: true, position: 'right', formatter: (p: { value: number }) => fmt(p.value) },
      },
    ],
  } as ChartOption
}

function barOption(items: { name: string; value: number }[], title: string, unit: string): ChartOption {
  if (items.length === 0) return emptyOption(title)
  return {
    title: { text: title, left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (rows: { name: string; value: number }[]) =>
        `${escapeHtml(rows[0]?.name ?? '')}：${fmt(rows[0]?.value)} ${unit}`,
    },
    legend: { bottom: 0, type: 'scroll', itemGap: 12 },
    grid: { left: 8, right: 16, top: 44, bottom: 56, containLabel: true },
    xAxis: {
      type: 'category',
      data: items.map((it) => it.name),
      axisTick: { alignWithLabel: true },
      axisLabel: { interval: 0, rotate: items.length > 6 ? 32 : 0, color: axisColor() },
    },
    yAxis: { type: 'value', name: unit, nameTextStyle: { color: '#909399' } },
    series: [
      {
        type: 'bar',
        barMaxWidth: 34,
        data: items.map((it) => it.value),
        itemStyle: { color: CHART_COLORS[0], borderRadius: [3, 3, 0, 0] },
      },
    ],
  } as ChartOption
}

/** 面积折线（趋势）：均值虚线 + 渐变填充 */
function trendOption(
  items: GroupCountItem[],
  title: string,
  unit: string,
  color: string = CHART_COLORS[0],
): ChartOption {
  if (items.length === 0) return emptyOption(title)
  return {
    title: { text: title, left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: {
      trigger: 'axis',
      formatter: (rows: { name: string; value: number }[]) =>
        `${escapeHtml(rows[0]?.name ?? '')}：${fmt(rows[0]?.value)} ${unit}`,
    },
    grid: { left: 8, right: 24, top: 44, bottom: 52, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: items.map((it) => monthLabel(it.key)),
      axisTick: { show: false },
      axisLabel: { interval: 0, rotate: items.length > 8 ? 32 : 0, color: axisColor() },
    },
    yAxis: { type: 'value', name: unit, nameTextStyle: { color: '#909399' } },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: items.map((it) => it.count),
        lineStyle: { width: 2.5, color },
        itemStyle: { color },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(64,158,255,0.35)' },
              { offset: 1, color: 'rgba(64,158,255,0.02)' },
            ],
          },
        },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { type: 'dashed', color: '#c0c4cc' },
          label: { formatter: '月均 {c}', fontSize: 10, color: '#909399' },
          data: [{ type: 'average' }],
        },
      },
    ],
  } as ChartOption
}

/** 分类 × 状态堆叠柱（各状态同色于状态饼图） */
function catStatusOption(
  rows: CategoryStatusItem[],
  statuses: string[],
  title: string,
  unit: string,
): ChartOption {
  if (rows.length === 0) return emptyOption(title)
  const totalOf = (c: string) => rows.filter((r) => r.category === c).reduce((a, b) => a + b.count, 0)
  const cats = [...new Set(rows.map((r) => r.category))].sort((a, b) => totalOf(b) - totalOf(a))
  return {
    title: { text: title, left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (rows2: { name: string; seriesName: string; value: number; axisValue: string }[]) =>
        `${escapeHtml(rows2[0]?.axisValue ?? '')}<br/>` +
        rows2.map((r) => `${escapeHtml(r.seriesName)}：${fmt(r.value)} ${unit}`).join('<br/>'),
    },
    legend: { bottom: 0, type: 'scroll', itemGap: 12 },
    grid: { left: 8, right: 16, top: 44, bottom: 56, containLabel: true },
    xAxis: {
      type: 'category',
      data: cats,
      axisTick: { alignWithLabel: true },
      axisLabel: { interval: 0, rotate: cats.length > 5 ? 32 : 0, color: axisColor() },
    },
    yAxis: { type: 'value', name: unit, nameTextStyle: { color: '#909399' } },
    series: statuses.map((s) => ({
      name: s,
      type: 'bar' as const,
      stack: 'total',
      barMaxWidth: 34,
      data: cats.map((c) => rows.find((r) => r.category === c && r.statusBiz === s)?.count ?? 0),
      itemStyle: { color: colorOf(s, statuses), borderRadius: s === statuses[statuses.length - 1] ? [3, 3, 0, 0] : undefined },
    })),
  } as ChartOption
}

/** 机房 U 位：容量/占用双柱 + 占用率折线（右轴） */
function roomOption(rooms: RoomUsageItem[], title: string): ChartOption {
  if (rooms.length === 0) return emptyOption(title)
  const names = rooms.map((r) => r.label || r.key)
  return {
    title: { text: title, left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { bottom: 0, type: 'scroll', itemGap: 12 },
    grid: { left: 8, right: 8, top: 46, bottom: 76, containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisTick: { alignWithLabel: true },
      axisLabel: { interval: 0, rotate: names.length > 6 ? 36 : 0, color: axisColor() },
    },
    yAxis: [
      { type: 'value', name: 'U 位', nameTextStyle: { color: '#909399' } },
      {
        type: 'value',
        name: '占用率',
        min: 0,
        max: 100,
        axisLabel: { formatter: '{value}%' },
        nameTextStyle: { color: '#909399' },
      },
    ],
    series: [
      {
        name: '容量 U',
        type: 'bar',
        barMaxWidth: 16,
        data: rooms.map((r) => r.uCap),
        itemStyle: { color: '#a0cfff', borderRadius: [3, 3, 0, 0] },
      },
      {
        name: '占用 U',
        type: 'bar',
        barMaxWidth: 16,
        data: rooms.map((r) => r.uOcc),
        itemStyle: { color: CHART_COLORS[0], borderRadius: [3, 3, 0, 0] },
      },
      {
        name: '占用率',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: rooms.map((r) => Number((r.rate * 100).toFixed(1))),
        itemStyle: { color: '#f56c6c' },
        lineStyle: { width: 2, color: '#f56c6c' },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { type: 'dashed', color: '#f56c6c' },
          label: { formatter: '均值 {c}%', fontSize: 10, color: '#f56c6c' },
          data: [{ type: 'average' }],
        },
      },
    ],
  } as ChartOption
}

/** 机柜 U 位占用率仪表盘 */
function gaugeOption(rate: number, title: string): ChartOption {
  const value = Number((rate * 100).toFixed(1))
  return {
    title: { text: title, left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    series: [
      {
        type: 'gauge',
        startAngle: 90,
        endAngle: -270,
        min: 0,
        max: 100,
        progress: { show: true, width: 14, roundCap: true, itemStyle: { color: CHART_COLORS[0] } },
        axisLine: { lineStyle: { width: 14, color: [[1, '#ebeef5']] } },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        pointer: { show: false },
        anchor: { show: false },
        title: { show: true, offsetCenter: [0, '36%'], fontSize: 13, color: '#909399' },
        detail: {
          valueAnimation: true,
          fontSize: 34,
          fontWeight: 700,
          color: value >= 85 ? '#f56c6c' : axisColor(),
          offsetCenter: [0, '-4%'],
          formatter: (v: number) => `${v.toFixed(1)}%`,
        },
        data: [{ value, name: 'U 位占用率' }],
      },
    ],
  } as ChartOption
}

/** 资产财务价值分析：原值/折旧/净值 堆叠柱 */
function assetValueOption(data: any): ChartOption {
  if (!data || !data.byCategory?.length) return emptyOption('资产财务价值分析')
  return {
    title: { text: '资产财务价值分析（按分类）', left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (rows: { name: string; seriesName: string; value: number }[]) =>
        `${escapeHtml(rows[0]?.name ?? '')}<br/>` +
        rows.map((r) => `${escapeHtml(r.seriesName)}：${fmt(r.value)} 元`).join('<br/>'),
    },
    legend: { bottom: 0, type: 'scroll', itemGap: 12 },
    grid: { left: 8, right: 16, top: 44, bottom: 56, containLabel: true },
    xAxis: {
      type: 'category',
      data: data.byCategory.map((c: any) => c.category),
      axisTick: { alignWithLabel: true },
      axisLabel: { interval: 0, rotate: data.byCategory.length > 5 ? 32 : 0, color: axisColor() },
    },
    yAxis: { type: 'value', name: '金额(元)', nameTextStyle: { color: '#909399' } },
    series: [
      {
        name: '原值',
        type: 'bar',
        stack: 'value',
        barMaxWidth: 24,
        data: data.byCategory.map((c: any) => c.originValue ?? 0),
        itemStyle: { color: CHART_COLORS[0] },
      },
      {
        name: '累计折旧',
        type: 'bar',
        stack: 'value',
        barMaxWidth: 24,
        data: data.byCategory.map((c: any) => c.accumulatedDep ?? 0),
        itemStyle: { color: CHART_COLORS[1] },
      },
      {
        name: '净值',
        type: 'bar',
        stack: 'value',
        barMaxWidth: 24,
        data: data.byCategory.map((c: any) => c.netValue ?? 0),
        itemStyle: { color: CHART_COLORS[2] },
      },
    ],
  } as ChartOption
}

/** 维修成本趋势：人工/外委/备件/其他 四类费用堆叠面积图 */
function repairCostOption(data: any): ChartOption {
  if (!data || !data.monthly?.length) return emptyOption('维修成本趋势分析')
  return {
    title: { text: '维修成本趋势（按月）', left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: {
      trigger: 'axis',
      formatter: (rows: { name: string; seriesName: string; value: number }[]) =>
        `${escapeHtml(rows[0]?.name ?? '')}<br/>` +
        rows.map((r) => `${escapeHtml(r.seriesName)}：${fmt(r.value)} 元`).join('<br/>'),
    },
    legend: { bottom: 0, type: 'scroll', itemGap: 12 },
    grid: { left: 8, right: 24, top: 44, bottom: 52, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.monthly.map((m: any) => monthLabel(m.month)),
      axisTick: { show: false },
      axisLabel: { interval: 0, rotate: data.monthly.length > 8 ? 32 : 0, color: axisColor() },
    },
    yAxis: { type: 'value', name: '金额(元)', nameTextStyle: { color: '#909399' } },
    series: [
      { name: '人工', type: 'line', smooth: true, stack: 'cost', data: data.monthly.map((m: any) => m.laborCost ?? 0), areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(64,158,255,0.35)' }, { offset: 1, color: 'rgba(64,158,255,0.02)' }] } }, lineStyle: { color: CHART_COLORS[0] }, itemStyle: { color: CHART_COLORS[0] } },
      { name: '外委', type: 'line', smooth: true, stack: 'cost', data: data.monthly.map((m: any) => m.serviceCost ?? 0), areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(250,173,20,0.35)' }, { offset: 1, color: 'rgba(250,173,20,0.02)' }] } }, lineStyle: { color: CHART_COLORS[1] }, itemStyle: { color: CHART_COLORS[1] } },
      { name: '备件', type: 'line', smooth: true, stack: 'cost', data: data.monthly.map((m: any) => m.partsCost ?? 0), areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(103,194,58,0.35)' }, { offset: 1, color: 'rgba(103,194,58,0.02)' }] } }, lineStyle: { color: CHART_COLORS[2] }, itemStyle: { color: CHART_COLORS[2] } },
      { name: '其他', type: 'line', smooth: true, stack: 'cost', data: data.monthly.map((m: any) => m.otherCost ?? 0), areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(245,108,108,0.35)' }, { offset: 1, color: 'rgba(245,108,108,0.02)' }] } }, lineStyle: { color: CHART_COLORS[3] }, itemStyle: { color: CHART_COLORS[3] } },
    ],
  } as ChartOption
}

/** SLA 达标率趋势：双轴（达标率% + 超期单数） */
function slaTrendOption(data: any): ChartOption {
  if (!data || !data.monthly?.length) return emptyOption('SLA 达标率趋势')
  return {
    title: { text: 'SLA 达标率趋势（按月）', left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, type: 'scroll', itemGap: 12 },
    grid: { left: 8, right: 50, top: 44, bottom: 52, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.monthly.map((m: any) => monthLabel(m.month)),
      axisTick: { show: false },
      axisLabel: { interval: 0, rotate: data.monthly.length > 8 ? 32 : 0, color: axisColor() },
    },
    yAxis: [
      { type: 'value', name: '达标率(%)', min: 0, max: 100, axisLabel: { formatter: '{value}%' }, nameTextStyle: { color: '#909399' } },
      { type: 'value', name: '超期单数', min: 0, axisLabel: { formatter: '{value} 单' }, nameTextStyle: { color: '#909399' } },
    ],
    series: [
      {
        name: '达标率',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: data.monthly.map((m: any) => (m.complianceRate ?? 0) * 100),
        lineStyle: { width: 2.5, color: CHART_COLORS[0] },
        itemStyle: { color: CHART_COLORS[0] },
        markLine: { silent: true, symbol: 'none', lineStyle: { type: 'dashed', color: '#f56c6c' }, label: { formatter: '及格线 90%', fontSize: 10 }, data: [{ yAxis: 90 }] },
      },
      {
        name: '超期单数',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 12,
        data: data.monthly.map((m: any) => m.overdueCount ?? 0),
        itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(245,108,108,0.7)' }, { offset: 1, color: 'rgba(245,108,108,0.1)' }] } },
      },
    ],
  } as ChartOption
}

/** 工单处理效率：全程/纯维修/响应 三组时长箱线图风格 */
function woEfficiencyOption(data: any): ChartOption {
  if (!data) return emptyOption('工单处理效率分析')
  const categories = ['全程时长', '纯维修时长', '响应时长']
  const avg = [data.avgHours ?? 0, data.avgRepairHours ?? 0, data.avgResponseHours ?? 0]
  const median = [data.medianHours ?? 0, data.medianRepairHours ?? 0, data.medianResponseHours ?? 0]
  const p90 = [data.p90Hours ?? 0, 0, 0]
  return {
    title: { text: '工单处理时长分布（小时）', left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { bottom: 0, type: 'scroll', itemGap: 12 },
    grid: { left: 8, right: 24, top: 44, bottom: 52, containLabel: true },
    xAxis: { type: 'category', data: categories, axisLabel: { color: axisColor() } },
    yAxis: { type: 'value', name: '小时', nameTextStyle: { color: '#909399' } },
    series: [
      { name: '平均值', type: 'bar', barMaxWidth: 18, data: avg, itemStyle: { color: CHART_COLORS[0] } },
      { name: '中位数', type: 'bar', barMaxWidth: 18, data: median, itemStyle: { color: CHART_COLORS[1] } },
      { name: 'P90', type: 'bar', barMaxWidth: 18, data: p90, itemStyle: { color: CHART_COLORS[2] } },
    ],
  } as ChartOption
}

/** 资产到期/续费预警：未来 12 个月到期资产数量 + 金额 */
function expiryAlertOption(data: any): ChartOption {
  if (!data || !data.expiryMonthly?.length) return emptyOption('资产到期/续费预警')
  return {
    title: { text: '未来 12 个月资产到期预警', left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: {
      trigger: 'axis',
      formatter: (rows: { name: string; seriesName: string; value: number }[]) =>
        `${escapeHtml(rows[0]?.name ?? '')}<br/>` +
        rows.map((r) => `${escapeHtml(r.seriesName)}：${fmt(r.value)}`).join('<br/>'),
    },
    legend: { bottom: 0, type: 'scroll', itemGap: 12 },
    grid: { left: 8, right: 16, top: 44, bottom: 56, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.expiryMonthly.map((m: any) => monthLabel(m.month)),
      axisTick: { show: false },
      axisLabel: { interval: 0, rotate: data.expiryMonthly.length > 8 ? 32 : 0, color: axisColor() },
    },
    yAxis: [
      { type: 'value', name: '台数', nameTextStyle: { color: '#909399' } },
      { type: 'value', name: '原值(万元)', nameTextStyle: { color: '#909399' } },
    ],
    series: [
      {
        name: '到期台数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        yAxisIndex: 0,
        data: data.expiryMonthly.map((m: any) => m.count ?? 0),
        lineStyle: { width: 2.5, color: '#f56c6c' },
        itemStyle: { color: '#f56c6c' },
      },
      {
        name: '原值金额',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 14,
        data: data.expiryMonthly.map((m: any) => (m.originValue ?? 0) / 10000),
        itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(245,108,108,0.7)' }, { offset: 1, color: 'rgba(245,108,108,0.1)' }] } },
      },
    ],
  } as ChartOption
}

/** 容量规划预测：机柜 U 位 / 功率 线性回归预测 */
function capacityForecastOption(data: any): ChartOption {
  if (!data || !data.forecast?.length) return emptyOption('容量规划预测')
  return {
    title: { text: '机柜 U 位容量预测（未来 12 个月）', left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, type: 'scroll', itemGap: 12 },
    grid: { left: 8, right: 24, top: 44, bottom: 52, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.forecast.map((f: any) => monthLabel(f.month)),
      axisTick: { show: false },
      axisLabel: { interval: 0, rotate: data.forecast.length > 8 ? 32 : 0, color: axisColor() },
    },
    yAxis: { type: 'value', name: 'U 位占用率(%)', min: 0, max: 100, axisLabel: { formatter: '{value}%' }, nameTextStyle: { color: '#909399' } },
    series: [
      {
        name: '历史占用率',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: data.forecast.map((f: any) => f.actualRate ?? null).filter((v: number | null) => v !== null),
        lineStyle: { width: 2.5, color: CHART_COLORS[0] },
        itemStyle: { color: CHART_COLORS[0] },
      },
      {
        name: '预测占用率',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: data.forecast.map((f: any) => f.predictedRate ?? null),
        lineStyle: { width: 2, type: 'dashed', color: '#f56c6c' },
        itemStyle: { color: '#f56c6c' },
      },
      {
        name: '预警线 85%',
        type: 'line',
        data: data.forecast.map(() => 85),
        lineStyle: { width: 1.5, type: 'dashed', color: '#f56c6c' },
        itemStyle: { color: '#f56c6c' },
        symbol: 'none',
        markLine: { silent: true, symbol: 'none', lineStyle: { type: 'dashed', color: '#f56c6c' }, data: [{ yAxis: 85 }] },
      },
    ],
  } as ChartOption
}

/** 资产投资回报分析：气泡图（X=利用率，Y=ROI，大小=投资额） */
function assetRoiOption(data: AssetRoiData | null): ChartOption {
  if (!data || !data.byCategory?.length) return emptyOption('资产投资回报分析')
  return {
    title: { text: '资产投资回报分析（按分类）', left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: {
      trigger: 'item',
      formatter: (p: { name: string; data: any }) =>
        `${escapeHtml(p.name)}<br/>投资额：${fmt(p.data.value[2])} 元<br/>利用率：${(p.data.value[0] * 100).toFixed(1)}%<br/>ROI：${(p.data.value[1] * 100).toFixed(1)}%<br/>回本周期：${p.data.paybackMonths?.toFixed(1) || '-'} 月`,
    },
    legend: { bottom: 0, type: 'scroll', itemGap: 12 },
    grid: { left: 8, right: 24, top: 44, bottom: 52, containLabel: true },
    xAxis: {
      type: 'value',
      name: '利用率(%)',
      min: 0,
      max: 100,
      axisLabel: { formatter: '{value}%' },
      nameTextStyle: { color: '#909399' },
    },
    yAxis: {
      type: 'value',
      name: 'ROI(%)',
      axisLabel: { formatter: '{value}%' },
      nameTextStyle: { color: '#909399' },
    },
    series: [
      {
        name: '投资回报',
        type: 'scatter',
        symbolSize: (val: number[]) => Math.max(10, Math.min(60, val[2] / 1e6 * 20)),
        data: data.byCategory.map((c, i) => ({
          name: c.category,
          value: [c.utilizationRate * 100, c.roi * 100, c.purchaseCost, c.paybackMonths],
          paybackMonths: c.paybackMonths,
          itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] },
        })),
      },
      {
        name: '基准线',
        type: 'line',
        data: [[0, 0], [100, 0]],
        lineStyle: { type: 'dashed', color: '#c0c4cc', width: 1 },
        symbol: 'none',
      },
    ],
  } as ChartOption
}

/** 运维成本结构：环形图 + 月度趋势堆叠面积图 */
function opexStructureOption(data: OpexStructureData | null): ChartOption {
  if (!data || !data.breakdown?.length) return emptyOption('运维成本结构分析')
  const hasTrend = data.monthlyTrend?.length > 0
  return {
    title: { text: hasTrend ? '运维成本结构与趋势' : '运维成本结构分析', left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: {
      trigger: hasTrend ? 'axis' : 'item',
      formatter: hasTrend
        ? (rows: { name: string; seriesName: string; value: number }[]) =>
            `${escapeHtml(rows[0]?.name ?? '')}<br/>` + rows.map((r) => `${escapeHtml(r.seriesName)}：${fmt(r.value)} 元`).join('<br/>')
        : (p: { name: string; value: number; percent: number }) =>
            `${escapeHtml(p.name)}：${fmt(p.value)} 元（${p.percent}%）`,
    },
    legend: { bottom: 0, type: 'scroll', itemGap: 12, orient: 'horizontal' },
    grid: hasTrend ? { left: 8, right: 24, top: 44, bottom: 52, containLabel: true } : { left: 'center', top: 40, bottom: 60, containLabel: true },
    xAxis: hasTrend
      ? {
          type: 'category',
          boundaryGap: false,
          data: data.monthlyTrend.map((m: any) => monthLabel(m.month)),
          axisTick: { show: false },
          axisLabel: { interval: 0, rotate: data.monthlyTrend.length > 8 ? 32 : 0, color: axisColor() },
        }
      : undefined,
    yAxis: hasTrend
      ? { type: 'value', name: '金额(元)', nameTextStyle: { color: '#909399' } }
      : undefined,
    series: hasTrend
      ? [
          { name: '电力', type: 'line', smooth: true, stack: 'opex', data: data.monthlyTrend.map((m: any) => m.power), areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(64,158,255,0.35)' }, { offset: 1, color: 'rgba(64,158,255,0.02)' }] } }, lineStyle: { color: CHART_COLORS[0] }, itemStyle: { color: CHART_COLORS[0] } },
          { name: '维修', type: 'line', smooth: true, stack: 'opex', data: data.monthlyTrend.map((m: any) => m.maintenance), areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(250,173,20,0.35)' }, { offset: 1, color: 'rgba(250,173,20,0.02)' }] } }, lineStyle: { color: CHART_COLORS[1] }, itemStyle: { color: CHART_COLORS[1] } },
          { name: '人力', type: 'line', smooth: true, stack: 'opex', data: data.monthlyTrend.map((m: any) => m.labor), areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(103,194,58,0.35)' }, { offset: 1, color: 'rgba(103,194,58,0.02)' }] } }, lineStyle: { color: CHART_COLORS[2] }, itemStyle: { color: CHART_COLORS[2] } },
          { name: '备件', type: 'line', smooth: true, stack: 'opex', data: data.monthlyTrend.map((m: any) => m.parts), areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(245,108,108,0.35)' }, { offset: 1, color: 'rgba(245,108,108,0.02)' }] } }, lineStyle: { color: CHART_COLORS[3] }, itemStyle: { color: CHART_COLORS[3] } },
          { name: '折旧', type: 'line', smooth: true, stack: 'opex', data: data.monthlyTrend.map((m: any) => m.depreciation), areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(144,147,153,0.35)' }, { offset: 1, color: 'rgba(144,147,153,0.02)' }] } }, lineStyle: { color: CHART_COLORS[4] }, itemStyle: { color: CHART_COLORS[4] } },
        ]
      : [
          {
            type: 'pie',
            radius: ['46%', '70%'],
            center: ['50%', '46%'],
            avoidLabelOverlap: true,
            itemStyle: { borderRadius: 5, borderColor: surfaceColor(), borderWidth: 2 },
            label: { show: true, fontSize: 11, lineHeight: 15, formatter: (p: { name: string; value: number; percent: number }) => `${p.name}\n${fmt(p.value)} (${p.percent}%)` },
            labelLine: { length: 8, length2: 6 },
            data: data.breakdown.map((b: any, i: number) => ({
              name: b.name,
              value: b.value,
              itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] },
            })),
          },
        ],
  } as ChartOption
}

/** 供应商绩效：雷达图/评分卡 */
function supplierPerformanceOption(data: SupplierPerformanceData | null): ChartOption {
  if (!data || !data.suppliers?.length) return emptyOption('供应商绩效分析')
  const topSuppliers = data.suppliers.slice(0, 8)
  const totalAmount = data.suppliers.reduce((a: number, b: any) => a + (b.totalAmount || 0), 0) || 1
  // 只绘制有真实数据的维度（交付及时率 / 响应时长平台未记录时为 null）
  const dims: { name: string; value: (s: any) => number | null }[] = [
    { name: '交付及时率(%)', value: (s: any) => (s.deliveryOnTimeRate == null ? null : s.deliveryOnTimeRate * 100) },
    { name: '质量合格率(%)', value: (s: any) => (s.qualityPassRate == null ? null : s.qualityPassRate * 100) },
    { name: '响应速度(反)', value: (s: any) => (s.avgResponseHours == null ? null : Math.max(0, 100 - (s.avgResponseHours / 24) * 100)) },
    { name: '合同覆盖度', value: (s: any) => Math.min(100, (s.totalContracts || 0) * 10) },
    { name: '金额占比(%)', value: (s: any) => Math.min(100, ((s.totalAmount || 0) / totalAmount) * 100) },
  ].filter((d) => topSuppliers.some((s: any) => d.value(s) != null))
  return {
    title: { text: '供应商绩效评分卡', left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll', itemGap: 12 },
    radar: {
      indicator: dims.map((d) => ({ name: d.name, max: 100 })),
      shape: 'polygon',
      splitNumber: 5,
      axisName: { color: axisColor() },
    },
    series: [
      {
        name: '供应商绩效',
        type: 'radar',
        data: topSuppliers.map((s: any, i: number) => ({
          name: s.supplierName,
          value: dims.map((d) => Math.round((d.value(s) ?? 0) * 10) / 10),
          itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] },
          lineStyle: { width: 2 },
          areaStyle: { opacity: 0.1 },
        })),
      },
    ],
  } as ChartOption
}

/** 资产全生命周期成本（TCO）：堆叠柱 */
function assetTcoOption(data: AssetTcoData | null): ChartOption {
  if (!data || !data.byCategory?.length) return emptyOption('资产全生命周期成本分析')
  return {
    title: { text: '资产全生命周期成本（TCO）分析', left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (rows: { name: string; seriesName: string; value: number }[]) =>
        `${escapeHtml(rows[0]?.name ?? '')}<br/>` +
        rows.map((r) => `${escapeHtml(r.seriesName)}：${fmt(r.value)} 元`).join('<br/>'),
    },
    legend: { bottom: 0, type: 'scroll', itemGap: 12 },
    grid: { left: 8, right: 16, top: 44, bottom: 56, containLabel: true },
    xAxis: {
      type: 'category',
      data: data.byCategory.map((c: any) => c.category),
      axisTick: { alignWithLabel: true },
      axisLabel: { interval: 0, rotate: data.byCategory.length > 5 ? 32 : 0, color: axisColor() },
    },
    yAxis: { type: 'value', name: '金额(元)', nameTextStyle: { color: '#909399' } },
    series: [
      { name: '采购成本', type: 'bar', stack: 'tco', barMaxWidth: 24, data: data.byCategory.map((c: any) => c.acquisitionCost ?? 0), itemStyle: { color: CHART_COLORS[0] } },
      { name: '维护成本', type: 'bar', stack: 'tco', barMaxWidth: 24, data: data.byCategory.map((c: any) => c.maintenanceCost ?? 0), itemStyle: { color: CHART_COLORS[1] } },
      { name: '能耗成本', type: 'bar', stack: 'tco', barMaxWidth: 24, data: data.byCategory.map((c: any) => c.energyCost ?? 0), itemStyle: { color: CHART_COLORS[2] } },
      { name: '折旧摊销', type: 'bar', stack: 'tco', barMaxWidth: 24, data: data.byCategory.map((c: any) => c.depreciation ?? 0), itemStyle: { color: CHART_COLORS[3] } },
      { name: '处置成本', type: 'bar', stack: 'tco', barMaxWidth: 24, data: data.byCategory.map((c: any) => c.disposalCost ?? 0), itemStyle: { color: CHART_COLORS[4] } },
    ],
  } as ChartOption
}

/** 容量规划多维预测：机柜/功率/制冷 三轴对比 */
function capacityPlanningOption(data: CapacityPlanningData | null): ChartOption {
  if (!data) return emptyOption('容量规划多维预测')
  const months = (data.rack ?? data.power)?.forecast?.map((f: any) => monthLabel(f.month)) ?? []
  return {
    title: { text: '容量规划多维预测（未来 12 个月）', left: 'center', top: 4, textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, type: 'scroll', itemGap: 12 },
    grid: { left: 8, right: 50, top: 44, bottom: 52, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: months,
      axisTick: { show: false },
      axisLabel: { interval: 0, rotate: months.length > 8 ? 32 : 0, color: axisColor() },
    },
    yAxis: [
      { type: 'value', name: '占用率(%)', min: 0, max: 100, axisLabel: { formatter: '{value}%' }, nameTextStyle: { color: '#909399' } },
      { type: 'value', name: '功率(kW)', min: 0, axisLabel: { formatter: '{value}kW' }, nameTextStyle: { color: '#909399' } },
    ],
    series: [
      {
        name: '机柜 U 位占用率',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        yAxisIndex: 0,
        data: data.rack?.forecast?.map((f: any) => f.predictedRate ?? null) ?? [],
        lineStyle: { width: 2.5, color: CHART_COLORS[0] },
        itemStyle: { color: CHART_COLORS[0] },
      },
      {
        name: '机柜预警线 85%',
        type: 'line',
        yAxisIndex: 0,
        data: months.map(() => 85),
        lineStyle: { width: 1, type: 'dashed', color: '#f56c6c' },
        symbol: 'none',
      },
      {
        name: '功率占用率',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        yAxisIndex: 0,
        data: data.power?.forecast?.map((f: any) => f.predictedRate ?? null) ?? [],
        lineStyle: { width: 2.5, color: CHART_COLORS[1] },
        itemStyle: { color: CHART_COLORS[1] },
      },
      {
        name: '功率预警线 80%',
        type: 'line',
        yAxisIndex: 0,
        data: months.map(() => 80),
        lineStyle: { width: 1, type: 'dashed', color: '#e6a23c' },
        symbol: 'none',
      },
      ...(data.cooling
        ? [
            {
              name: '制冷占用率',
              type: 'line',
              smooth: true,
              symbol: 'circle',
              symbolSize: 5,
              yAxisIndex: 0,
              data: data.cooling.forecast?.map((f: any) => f.predictedRate ?? null) ?? [],
              lineStyle: { width: 2.5, color: CHART_COLORS[2] },
              itemStyle: { color: CHART_COLORS[2] },
            },
          ]
        : []),
    ],
  } as ChartOption
}

/* ---------------- 面板摘要（缺失维度不显示，避免 NaN%） ---------------- */
function pct(part?: { currentUsage?: number; totalCapacity?: number } | null): string | null {
  if (!part || !part.totalCapacity) return null
  return ((part.currentUsage ?? 0) / part.totalCapacity * 100).toFixed(1) + '%'
}

function capacityExtra(): string {
  const c = capacityData.value
  if (!c) return '暂无容量数据'
  const parts = [
    pct(c.rack) && `机柜 ${pct(c.rack)}`,
    pct(c.power) && `功率 ${pct(c.power)}`,
    pct(c.cooling) && `制冷 ${pct(c.cooling)}`,
  ].filter(Boolean)
  return parts.length ? parts.join(' · ') : '未配置机柜容量 / 额定功率'
}

function supplierExtra(): string {
  const list = supplierData.value?.suppliers ?? []
  if (!list.length) return '暂无供应商数据'
  const scored = list.filter((x) => typeof x.score === 'number')
  const avg = scored.length ? (scored.reduce((a, b) => a + (b.score as number), 0) / scored.length).toFixed(1) : '-'
  return `${list.length} 家供应商 · 平均分 ${avg}`
}

/* ---------------- v3.5 卡片布局配置（显隐/排序，localStorage 持久化） ---------------- */
const LAYOUT_KEY = 'dcim.dashboard.layout'

interface DashboardLayout {
  /** 隐藏的图表 key */
  hidden: string[]
  /** 显示顺序（未列出的按默认顺序排在末尾） */
  order: string[]
}

function loadLayout(): DashboardLayout {
  try {
    const raw = localStorage.getItem(LAYOUT_KEY)
    if (raw) {
      const v = JSON.parse(raw)
      if (Array.isArray(v?.hidden) && Array.isArray(v?.order)) {
        return {
          hidden: (v.hidden as unknown[]).filter((x): x is string => typeof x === 'string'),
          order: (v.order as unknown[]).filter((x): x is string => typeof x === 'string'),
        }
      }
    }
  } catch {
    // 配置损坏时回退默认布局
  }
  return { hidden: [], order: [] }
}

const layout = ref<DashboardLayout>(loadLayout())

/* ---------------- 图表清单 ---------------- */
const allChartDefs = computed(() => {
  const d = data.value
  const statuses = d?.byStatusBiz.map((s) => s.key) ?? []
  const wo = d?.woStats
  const woItems = wo
    ? [
        { name: '待处理', value: wo.pending },
        { name: '待审批', value: wo.approving },
        { name: '处理中', value: wo.processing },
        { name: '已完成', value: wo.done },
        { name: '已驳回', value: wo.rejected },
        { name: '已关闭', value: wo.closed },
      ]
    : []

  return [
    // ========== 核心运营指标（精简保留） ==========
    {
      key: 'status',
      title: '资产状态分布',
      extra: () => `${fmt(d?.assetTotal ?? 0)} 台`,
      option: () => donutOption(d?.byStatusBiz ?? [], '资产状态分布', '台', (k) => k),
    },
    {
      key: 'region',
      title: '区域资产分布',
      extra: () => `${d?.byRegion.length ?? 0} 个区域`,
      option: () => hBarOption(d?.byRegion ?? [], '区域资产分布', '台'),
    },
    // ========== 财务决策图表 ==========
    {
      key: 'assetValue',
      title: '资产财务价值分析',
      extra: () => assetFinanceData.value ? `原值 ${fmt(assetFinanceData.value.totalOriginValue)} · 净值 ${fmt(assetFinanceData.value.totalNetValue)} · 减值 ${fmt(assetFinanceData.value.totalImpairment)}` : '暂无财务数据',
      option: () => assetValueOption(assetFinanceData.value),
    },
    // ROI 依赖营收数据（平台不采集）：后端未提供时不展示该面板，避免空图或编造数字
    ...(roiData.value
      ? [
          {
            key: 'assetRoi',
            title: '资产投资回报分析（ROI）',
            extra: () => roiData.value ? `总投资 ${fmt(roiData.value.summary.totalInvestment)} · 平均ROI ${(roiData.value.summary.avgRoi * 100).toFixed(1)}%` : '',
            option: () => assetRoiOption(roiData.value),
          },
        ]
      : []),
    {
      key: 'assetTco',
      title: '资产全生命周期成本（TCO）',
      extra: () => tcoData.value ? `总 TCO ${fmt(tcoData.value.summary.totalTco)} · 年均 ${fmt(tcoData.value.summary.avgAnnualTco)}` : '暂无 TCO 数据',
      option: () => assetTcoOption(tcoData.value),
    },
    {
      key: 'opexStructure',
      title: '运维成本结构分析',
      extra: () => opexData.value ? `总成本 ${fmt(opexData.value.total)} · ${opexData.value.breakdown?.length ?? 0} 项构成` : '暂无成本数据',
      option: () => opexStructureOption(opexData.value),
    },
    // ========== 运维效率图表 ==========
    {
      key: 'repairCost',
      title: '维修成本趋势分析',
      extra: () => repairCostData.value ? `人工 ${fmt(repairCostData.value.summary.laborValue)} · 外委 ${fmt(repairCostData.value.summary.serviceValue)} · 备件 ${fmt(repairCostData.value.summary.partsValue)}` : '暂无维修成本数据',
      option: () => repairCostOption(repairCostData.value),
    },
    {
      key: 'slaTrend',
      title: 'SLA 达标率趋势',
      extra: () => slaData.value ? `达标率 ${(slaData.value.complianceRate * 100).toFixed(1)}% · 超期 ${fmt(slaData.value.overdueCount)} 单` : '暂无 SLA 数据',
      option: () => slaTrendOption(slaData.value),
    },
    {
      key: 'woEfficiency',
      title: '工单处理效率分析',
      extra: () => repairDurationData.value ? `平均全程 ${repairDurationData.value.avgHours?.toFixed(1) || '-'}h · 中位 ${repairDurationData.value.medianHours?.toFixed(1) || '-'}h · 响应率 ${(repairDurationData.value.respondRate * 100).toFixed(1)}%` : '暂无效率数据',
      option: () => woEfficiencyOption(repairDurationData.value),
    },
    // ========== 供应链/风险图表 ==========
    {
      key: 'supplierPerformance',
      title: '供应商绩效评分卡',
      extra: () => supplierExtra(),
      option: () => supplierPerformanceOption(supplierData.value),
    },
    {
      key: 'expiryAlert',
      title: '资产到期/续费预警',
      extra: () => data.value ? `到期 ${fmt(data.value.expiryCount ?? 0)} 台 · 30天内 ${fmt(data.value.expiry30d ?? 0)} 台` : '暂无预警数据',
      option: () => expiryAlertOption(data.value),
    },
    // ========== 容量规划图表 ==========
    {
      key: 'capacityForecast',
      title: '机柜 U 位容量预测',
      extra: () => data.value ? `机柜 U 位 ${usagePercent()}% · 预计 ${data.value.forecastMonths ?? 6} 个月达 85%` : '暂无预测数据',
      option: () => capacityForecastOption(data.value),
    },
    {
      key: 'capacityPlanning',
      title: '容量规划多维预测（U位/功率/制冷）',
      extra: () => capacityExtra(),
      option: () => capacityPlanningOption(capacityData.value),
    },
  ]
})

/** 应用布局后的图表清单：过滤隐藏 + 按 order 排序（未列出的按默认顺序排在末尾） */
const chartList = computed(() => {
  const { hidden, order } = layout.value
  const rank = (key: string): number => {
    const i = order.indexOf(key)
    return i < 0 ? order.length : i
  }
  return allChartDefs.value
    .filter((c) => !hidden.includes(c.key))
    .slice()
    .sort((a, b) => rank(a.key) - rank(b.key))
})

/* ---------------- v3.5 布局编辑抽屉（勾选显隐 + 上下移排序，不做拖拽） ---------------- */
const layoutDrawer = ref(false)
/** 草稿（保存前不影响渲染） */
const draftOrder = ref<string[]>([])
const draftHidden = ref<string[]>([])

function openLayout() {
  const shown = chartList.value.map((c) => c.key as string)
  const hiddenKeys = allChartDefs.value.map((c) => c.key as string).filter((k) => !shown.includes(k))
  draftOrder.value = [...shown, ...hiddenKeys]
  draftHidden.value = [...layout.value.hidden]
  layoutDrawer.value = true
}

function draftVisible(key: string): boolean {
  return !draftHidden.value.includes(key)
}

function toggleDraft(key: string, visible: string | number | boolean) {
  if (visible) draftHidden.value = draftHidden.value.filter((k) => k !== key)
  else if (!draftHidden.value.includes(key)) draftHidden.value.push(key)
}

function draftTitle(key: string): string {
  return allChartDefs.value.find((c) => (c.key as string) === key)?.title ?? key
}

function moveDraft(i: number, dir: -1 | 1) {
  const j = i + dir
  const arr = draftOrder.value
  if (j < 0 || j >= arr.length) return
  const t = arr[i]
  arr[i] = arr[j]
  arr[j] = t
}

function saveLayout() {
  layout.value = {
    hidden: draftHidden.value.filter((k) => draftOrder.value.includes(k)),
    order: [...draftOrder.value],
  }
  localStorage.setItem(LAYOUT_KEY, JSON.stringify(layout.value))
  layoutDrawer.value = false
}

function resetLayout() {
  layout.value = { hidden: [], order: [] }
  localStorage.removeItem(LAYOUT_KEY)
  layoutDrawer.value = false
}

/* ---------------- 图表实例管理（按需初始化 + resize + 卸载销毁） ---------------- */
const boxes = new Map<string, HTMLDivElement>()
const instances = new Map<string, EChartsType>()

function setBox(key: string, el: unknown) {
  if (el instanceof HTMLDivElement) {
    boxes.set(key, el)
  } else {
    const old = instances.get(key)
    if (old) {
      old.dispose()
      instances.delete(key)
    }
    boxes.delete(key)
  }
}

/** 当前是否暗色主题（html.dark 由主题 store 维护） */
function isDark(): boolean {
  return document.documentElement.classList.contains('dark')
}
/** 已初始化实例对应的主题；主题切换时需销毁重建（ECharts 主题只能在 init 时指定） */
let instanceTheme: 'dark' | 'light' = 'light'

function draw(key: string, option: ChartOption) {
  const el = boxes.get(key)
  if (!el) return
  let chart = instances.get(key)
  if (!chart) {
    chart = echarts.init(el, isDark() ? 'dark' : undefined)
    instances.set(key, chart)
  }
  // 背景透明：交给卡片背景，明暗两套都协调
  chart.setOption({ backgroundColor: 'transparent', ...(option as object) } as ChartOption, true)
}

function renderCharts() {
  if (!data.value) return
  const theme = isDark() ? 'dark' : 'light'
  if (theme !== instanceTheme) {
    instances.forEach((c) => c.dispose())
    instances.clear()
    instanceTheme = theme
  }
  for (const c of chartList.value) {
    draw(c.key as ChartKey, c.option())
  }
}

/* ---------------- 响应式（列数 + 图表尺寸） ---------------- */
const colSpan = ref(8)

function updateLayout() {
  const w = window.innerWidth
  colSpan.value = w < 768 ? 24 : w < 1280 ? 12 : 8
}

function handleResize() {
  updateLayout()
  instances.forEach((chart) => chart.resize())
}

// 切换明/暗主题后按新配色重绘
function handleThemeChange() {
  void nextTick(() => renderCharts())
}

/** v3.5：布局变化（显隐/排序）后按新卡片集合重绘 */
watch(chartList, () => void nextTick(() => renderCharts()))

onMounted(() => {
  updateLayout()
  if (canSeeReport.value) void load()
  window.addEventListener('resize', handleResize)
  window.addEventListener('dcim-theme-change', handleThemeChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('dcim-theme-change', handleThemeChange)
  instances.forEach((chart) => chart.dispose())
  instances.clear()
  boxes.clear()
})
</script>

<template>
  <el-result
    v-if="!canSeeReport"
    icon="info"
    title="运营总览需要「数据报表」读权限"
    sub-title="请从左侧菜单进入有权限的模块，或联系管理员为当前角色开通报表权限"
  />
  <div v-else v-loading="loading" class="dashboard">
    <!-- v3.5 布局设置入口 -->
    <div class="layout-bar">
      <el-button size="small" plain @click="openLayout">布局设置</el-button>
    </div>

    <!-- 顶部统计卡 -->
    <el-row :gutter="16">
      <el-col :span="colSpan === 24 ? 24 : 6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">资产总数</div>
          <div class="stat-value">{{ fmt(data?.assetTotal) }}</div>
          <div class="stat-sub">
            在用 {{ fmt(statusCount('在用')) }} · 维修 {{ fmt(statusCount('维修')) }} ·
            分类 {{ data?.byCategory.length ?? 0 }} 类
          </div>
        </el-card>
      </el-col>
      <el-col :span="colSpan === 24 ? 24 : 6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">待处理工单</div>
          <div class="stat-value">{{ data ? fmt(pendingTotal()) : '-' }}</div>
          <div class="stat-sub">
            待处理 {{ data?.woStats.pending ?? 0 }} · 待审批 {{ data?.woStats.approving ?? 0 }} ·
            SLA 超期 {{ data?.woStats.slaOverdue ?? 0 }}
          </div>
        </el-card>
      </el-col>
      <el-col :span="colSpan === 24 ? 24 : 6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">机柜使用率</div>
          <div class="stat-value">{{ data ? usagePercent() + '%' : '-' }}</div>
          <div class="stat-sub">
            已占 {{ fmt(data?.rackUsage.usedUnits) }}U / 共 {{ fmt(data?.rackUsage.totalUnits) }}U
          </div>
        </el-card>
      </el-col>
      <el-col :span="colSpan === 24 ? 24 : 6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">近 30 天新增上架</div>
          <div class="stat-value">{{ data ? fmt(data.recent30dRacked) : '-' }}</div>
          <div class="stat-sub">{{ rackedThisAndLastMonth() }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区：9 图，窗口越窄列数越少 -->
    <el-row :gutter="16" class="chart-row">
      <el-col
        v-for="c in chartList"
        :key="c.key"
        :span="colSpan"
        class="chart-col"
      >
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">{{ c.title }}</span>
              <span class="card-extra">{{ c.extra() }}</span>
            </div>
          </template>
          <div :ref="(el) => setBox(c.key, el)" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- v3.5 布局编辑：勾选显隐 + 上下移排序（不做拖拽） -->
    <el-drawer v-model="layoutDrawer" title="布局设置" size="440px">
      <div class="layout-tip">勾选要显示的图表卡片；「上移 / 下移」调整顺序，保存后立即生效并记住选择。</div>
      <div v-for="(k, i) in draftOrder" :key="k" class="layout-row">
        <el-checkbox :model-value="draftVisible(k)" @change="(v: string | number | boolean) => toggleDraft(k, v)">
          {{ draftTitle(k) }}
        </el-checkbox>
        <span class="layout-ops">
          <el-button :disabled="i === 0" link size="small" @click="moveDraft(i, -1)">上移</el-button>
          <el-button :disabled="i === draftOrder.length - 1" link size="small" @click="moveDraft(i, 1)">下移</el-button>
        </span>
      </div>
      <template #footer>
        <el-button @click="resetLayout">恢复默认</el-button>
        <el-button type="primary" @click="saveLayout">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.layout-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}
.layout-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
  line-height: 1.6;
}
.layout-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.layout-ops {
  white-space: nowrap;
}
.stat-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.4;
}

.stat-sub {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.chart-row {
  margin-top: 16px;
}

.chart-col {
  margin-bottom: 16px;
}

.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.card-extra {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
}

.chart-box {
  height: 300px;
  width: 100%;
}
</style>
