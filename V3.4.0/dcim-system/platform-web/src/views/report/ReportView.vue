<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import echarts, { CHART_COLORS, type EChartsType } from '@/utils/echarts'
import {
  fetchAssetReport,
  fetchRepairCost,
  fetchRepairDuration,
  fetchRepairMaterials,
  fetchRepairTrend,
  fetchWorkorderReport,
  fetchWorkorderSla,
  fetchWarehouseReport,
} from '@/api/report'
import { fetchWarehouseOptions } from '@/api/warehouse'
import type { WarehouseOption } from '@/api/warehouse'
import type {
  AssetGroupBy,
  GroupCountItem,
  GroupValueItem,
  RepairCostData,
  RepairDurationData,
  RepairMaterialsData,
  RepairTrendPoint,
  SlaData,
  WarehouseAggItem,
  WarehouseData,
  WorkorderGroupBy,
} from '@/api/report'

/* ---------------- 资产报表 ---------------- */
const assetGroup = ref<AssetGroupBy>('statusBiz')
const assetLoading = ref(false)
const assetRows = ref<GroupValueItem[]>([])

const assetGroupOptions: { value: AssetGroupBy; label: string }[] = [
  { value: 'statusBiz', label: '业务状态' },
  { value: 'regionCode', label: '区域' },
  { value: 'categoryCode', label: '资产分类' },
  { value: 'ownerType', label: '权属类型' },
]

function assetGroupLabel(key: string): string {
  return assetGroupOptions.find((o) => o.value === key)?.label ?? key
}

function ownerTypeLabel(key: string): string {
  return key === 'SELF' ? 'SELF（自持）' : key === 'LEASE' ? 'LEASE（融资租赁）' : key
}

/** 行 key 的展示名（权属类型译成中文，其余原样） */
function rowLabel(groupBy: string, key: string): string {
  return groupBy === 'ownerType' ? ownerTypeLabel(key) : key
}

async function loadAssetReport() {
  assetLoading.value = true
  try {
    assetRows.value = (await fetchAssetReport(assetGroup.value)) ?? []
    await nextTick()
    renderAssetChart()
  } catch {
    // 拦截器已提示
  } finally {
    assetLoading.value = false
  }
}

/* ---------------- 工单报表 ---------------- */
const woGroup = ref<WorkorderGroupBy>('status')
const woLoading = ref(false)
const woRows = ref<GroupCountItem[]>([])
const sla = ref<SlaData | null>(null)

const woGroupOptions: { value: WorkorderGroupBy; label: string }[] = [
  { value: 'status', label: '状态' },
  { value: 'woType', label: '工单类型' },
  { value: 'priority', label: '优先级' },
  { value: 'rootCause', label: '根因' },
]

function woGroupLabel(key: string): string {
  return woGroupOptions.find((o) => o.value === key)?.label ?? key
}

async function loadWorkorderReport() {
  woLoading.value = true
  try {
    const [rows, slaData] = await Promise.all([fetchWorkorderReport(woGroup.value), fetchWorkorderSla()])
    woRows.value = rows ?? []
    sla.value = slaData
    await nextTick()
    renderWoChart()
  } catch {
    // 拦截器已提示
  } finally {
    woLoading.value = false
  }
}

/* ---------------- 仓储报表 ---------------- */
const whLoading = ref(false)
const whData = ref<WarehouseData | null>(null)
/** 仓库维度筛选：空 = 全部物料 */
const whWarehouseCode = ref('')
const whWarehouseOpts = ref<WarehouseOption[]>([])

async function loadWarehouseOpts() {
  try {
    whWarehouseOpts.value = await fetchWarehouseOptions(true)
  } catch {
    whWarehouseOpts.value = []
  }
}

async function loadWarehouseReport() {
  whLoading.value = true
  try {
    whData.value = await fetchWarehouseReport(whWarehouseCode.value || undefined)
  } catch {
    // 拦截器已提示
  } finally {
    whLoading.value = false
  }
}

/** 按仓库分组（含空仓 = 该仓无货） */
const byWarehouse = computed<WarehouseAggItem[]>(() => whData.value?.byWarehouse ?? [])

/** 空仓视图：stockQty = 0 */
const emptyWarehouses = computed(() => byWarehouse.value.filter((w) => w.empty))

function warehouseName(code: string): string {
  return whWarehouseOpts.value.find((w) => w.warehouseCode === code)?.name || code
}

/* ---------------- 维修分析（时长 / 趋势 / 备件 / 费用） ---------------- */
type Granularity = 'day' | 'week' | 'month'

/** yyyy-MM-dd（本机日期） */
function fmtDay(offsetDays: number): string {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const repairRange = ref<[string, string]>([fmtDay(-29), fmtDay(0)])
const repairGranularity = ref<Granularity>('month')
const repairLoading = ref(false)
const repairDur = ref<RepairDurationData | null>(null)
const repairTrend = ref<RepairTrendPoint[]>([])
const repairMat = ref<RepairMaterialsData | null>(null)
const repairCost = ref<RepairCostData | null>(null)
/** 根因分组：口径与维修分析一致（根因字段只在完工/关闭时写） */
const repairCauses = ref<GroupCountItem[]>([])

async function loadRepairReport() {
  repairLoading.value = true
  const [from, to] = repairRange.value ?? [undefined, undefined]
  try {
    const [dur, trend, mat, cost, causes] = await Promise.all([
      fetchRepairDuration(from, to),
      fetchRepairTrend(from, to, repairGranularity.value),
      fetchRepairMaterials(from, to),
      fetchRepairCost(from, to),
      fetchWorkorderReport('rootCause'),
    ])
    repairDur.value = dur
    repairTrend.value = trend ?? []
    repairMat.value = mat
    repairCost.value = cost
    repairCauses.value = causes ?? []
    await nextTick()
    if (activeTab.value === 'repair') renderRepairChart()
  } catch {
    // 拦截器已提示
  } finally {
    repairLoading.value = false
  }
}

/** 已完工单里的根因占比 */
function causeShare(count: number): string {
  const done = repairDur.value?.doneCount ?? 0
  if (!done) return '-'
  return `${((count / done) * 100).toFixed(1)}%`
}

/** 时长展示：无样本显示 '-' */
function hours(v?: number | null): string {
  if (v === null || v === undefined) return '-'
  return Number(v).toFixed(2)
}

/** 备件消耗汇总行（卡片头） */
function consumptionHead(): string {
  const s = repairMat.value?.summary
  if (!s) return '暂无出库流水'
  return `${s.woCount} 单｜${s.materialCount} 种物料｜数量 ${s.totalQty}｜金额 ${money(s.totalValue)} 元`
}

/** 四类费用合计（元） */
const costTotal = computed(() => {
  const s = repairCost.value?.summary
  if (!s) return '-'
  return money(s.laborValue + s.serviceValue + s.otherValue + s.partsValue)
})

/* ---------------- 图表（按需引入，resize 自适应 + 卸载销毁） ---------------- */
const activeTab = ref('asset')
const assetChartRef = ref<HTMLDivElement>()
const woChartRef = ref<HTMLDivElement>()
const repairChartRef = ref<HTMLDivElement>()
let assetChart: EChartsType | null = null
let woChart: EChartsType | null = null
let repairChart: EChartsType | null = null

function renderAssetChart() {
  if (!assetChartRef.value) return
  assetChart = assetChart || echarts.init(assetChartRef.value)
  assetChart.setOption({
    title: {
      text: '采购金额合计（元）',
      left: 'center',
      textStyle: { fontSize: 14 },
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (v: number) => `${Number(v ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })} 元`,
    },
    grid: { left: 70, right: 16, top: 40, bottom: 56 },
    xAxis: {
      type: 'category',
      data: assetRows.value.map((r) => rowLabel(assetGroup.value, r.key)),
      axisLabel: { interval: 0, rotate: assetRows.value.length > 5 ? 20 : 0 },
    },
    yAxis: { type: 'value', name: '元' },
    series: [
      {
        name: '采购金额合计',
        type: 'bar',
        barMaxWidth: 40,
        itemStyle: { color: CHART_COLORS[0], borderRadius: [3, 3, 0, 0] },
        data: assetRows.value.map((r) => r.totalValue ?? 0),
      },
    ],
  })
  assetChart.resize()
}

function renderWoChart() {
  if (!woChartRef.value) return
  woChart = woChart || echarts.init(woChartRef.value)
  woChart.setOption({
    title: { text: '工单数量', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 40, right: 16, top: 40, bottom: 56 },
    xAxis: {
      type: 'category',
      data: woRows.value.map((r) => r.label || r.key),
      axisLabel: { interval: 0, rotate: woRows.value.length > 5 ? 20 : 0 },
    },
    yAxis: { type: 'value', name: '单' },
    series: [
      {
        name: '工单数量',
        type: 'bar',
        barMaxWidth: 40,
        itemStyle: { color: CHART_COLORS[1], borderRadius: [3, 3, 0, 0] },
        data: woRows.value.map((r) => r.count),
      },
    ],
  })
  woChart.resize()
}

function renderRepairChart() {
  if (!repairChartRef.value) return
  repairChart = repairChart || echarts.init(repairChartRef.value)
  repairChart.setOption({
    title: { text: '维修工单趋势', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, data: ['工单数', '完工数', '平均全程时长(h)'] },
    grid: { left: 44, right: 56, top: 44, bottom: 72 },
    xAxis: {
      type: 'category',
      data: repairTrend.value.map((r) => r.bucket),
      axisLabel: { interval: 'auto', rotate: repairTrend.value.length > 12 ? 20 : 0 },
    },
    yAxis: [
      { type: 'value', name: '单' },
      { type: 'value', name: '小时', splitLine: { show: false } },
    ],
    series: [
      {
        name: '工单数',
        type: 'bar',
        barMaxWidth: 22,
        itemStyle: { color: CHART_COLORS[1] },
        data: repairTrend.value.map((r) => r.count),
      },
      {
        name: '完工数',
        type: 'bar',
        barMaxWidth: 22,
        itemStyle: { color: CHART_COLORS[0] },
        data: repairTrend.value.map((r) => r.doneCount),
      },
      {
        name: '平均全程时长(h)',
        type: 'line',
        yAxisIndex: 1,
        // 无完工单的桶 avgHours 缺省 → 断点显示，避免把「没数据」画成 0
        connectNulls: false,
        itemStyle: { color: CHART_COLORS[2] },
        data: repairTrend.value.map((r) => r.avgHours ?? null),
      },
    ],
  })
  repairChart.resize()
}

function handleResize() {
  assetChart?.resize()
  woChart?.resize()
  repairChart?.resize()
}

async function onTabChange() {
  await nextTick()
  if (activeTab.value === 'asset') {
    renderAssetChart()
  } else if (activeTab.value === 'workorder') {
    renderWoChart()
  } else if (activeTab.value === 'repair') {
    renderRepairChart()
  }
}

function ratePercent(rate?: number | null): string {
  if (rate === null || rate === undefined) return '-'
  return `${(rate * 100).toFixed(2)}%`
}

/* ---------------- 预警展示 ---------------- */
function alertText(alert?: string): string {
  if (alert === 'OUT') return '缺货'
  if (alert === 'LOW') return '低库存'
  return '-'
}

function alertTagType(alert?: string): 'danger' | 'warning' | 'info' {
  if (alert === 'OUT') return 'danger'
  if (alert === 'LOW') return 'warning'
  return 'info'
}

/** 预警行标红 */
function rowClass({ row }: { row: { alert?: string } }): string {
  return row.alert === 'OUT' ? 'row-danger' : row.alert === 'LOW' ? 'row-warning' : ''
}

function money(v?: number): string {
  return Number(v ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

onMounted(() => {
  void loadAssetReport()
  void loadWorkorderReport()
  void loadWarehouseOpts()
  void loadWarehouseReport()
  void loadRepairReport()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  assetChart?.dispose()
  woChart?.dispose()
  repairChart?.dispose()
  assetChart = null
  woChart = null
  repairChart = null
})
</script>

<template>
  <div class="report-page">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ────────── 资产报表 ────────── -->
      <el-tab-pane label="资产报表" name="asset">
        <div class="toolbar">
          <span class="toolbar-label">分组维度：</span>
          <el-select v-model="assetGroup" style="width: 160px" @change="loadAssetReport">
            <el-option
              v-for="o in assetGroupOptions"
              :key="o.value"
              :value="o.value"
              :label="o.label"
            />
          </el-select>
          <el-button :loading="assetLoading" @click="loadAssetReport">刷新</el-button>
        </div>
        <el-row :gutter="16" v-loading="assetLoading">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>资产「{{ assetGroupLabel(assetGroup) }}」分组统计</template>
              <el-table :data="assetRows" size="small" max-height="380">
                <el-table-column label="分组" min-width="140">
                  <template #default="{ row }">{{ rowLabel(assetGroup, row.key) }}</template>
                </el-table-column>
                <el-table-column prop="count" label="数量（台）" width="110" align="right" />
                <el-table-column label="采购金额合计（元）" min-width="150" align="right">
                  <template #default="{ row }">{{ money(row.totalValue) }}</template>
                </el-table-column>
              </el-table>
              <el-empty
                v-if="!assetLoading && assetRows.length === 0"
                description="暂无资产数据"
                :image-size="60"
              />
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <div ref="assetChartRef" class="chart-box"></div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- ────────── 工单报表 ────────── -->
      <el-tab-pane label="工单报表" name="workorder">
        <div class="toolbar">
          <span class="toolbar-label">分组维度：</span>
          <el-select v-model="woGroup" style="width: 160px" @change="loadWorkorderReport">
            <el-option v-for="o in woGroupOptions" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
          <el-button :loading="woLoading" @click="loadWorkorderReport">刷新</el-button>
        </div>
        <el-row :gutter="16" v-loading="woLoading">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>工单「{{ woGroupLabel(woGroup) }}」分组统计</template>
              <el-table :data="woRows" size="small" max-height="260">
                <el-table-column label="分组" min-width="140">
                  <template #default="{ row }">{{ row.label || row.key }}</template>
                </el-table-column>
                <el-table-column prop="count" label="数量（单）" width="110" align="right" />
              </el-table>
              <el-empty
                v-if="!woLoading && woRows.length === 0"
                description="暂无工单数据"
                :image-size="60"
              />
            </el-card>
            <!-- SLA 卡片 -->
            <el-card shadow="never" class="sla-card">
              <template #header>SLA 达成情况</template>
              <el-row :gutter="16">
                <el-col :span="6">
                  <div class="sla-item">
                    <div class="sla-value">{{ sla?.total ?? '-' }}</div>
                    <div class="sla-label">工单总数</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="sla-item">
                    <div class="sla-value">{{ sla?.respondedCount ?? '-' }}</div>
                    <div class="sla-label">已响应</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="sla-item">
                    <div class="sla-value">{{ ratePercent(sla?.responseRate) }}</div>
                    <div class="sla-label">响应率</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="sla-item">
                    <div
                      class="sla-value"
                      :class="{ 'stat-danger': (sla?.overdueCount ?? 0) > 0 }"
                    >
                      {{ sla?.overdueCount ?? '-' }}
                    </div>
                    <div class="sla-label">SLA 超期</div>
                  </div>
                </el-col>
              </el-row>
              <div class="sla-compliance">
                达标率：<b>{{ ratePercent(sla?.complianceRate) }}</b>
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <div ref="woChartRef" class="chart-box"></div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- ────────── 维修分析 ────────── -->
      <el-tab-pane label="维修分析" name="repair">
        <div class="toolbar">
          <span class="toolbar-label">日期范围：</span>
          <el-date-picker
            v-model="repairRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="起始日期"
            end-placeholder="结束日期"
            :clearable="false"
            style="width: 260px"
            @change="loadRepairReport"
          />
          <span class="toolbar-label">粒度：</span>
          <el-select
            v-model="repairGranularity"
            style="width: 110px"
            @change="loadRepairReport"
          >
            <el-option label="按日" value="day" />
            <el-option label="按周" value="week" />
            <el-option label="按月" value="month" />
          </el-select>
          <el-button :loading="repairLoading" @click="loadRepairReport">刷新</el-button>
          <span class="toolbar-hint">口径：仅「故障维修 / 部件更换」工单；备件费按出库流水反算</span>
        </div>

        <!-- KPI：时长分位 -->
        <el-row :gutter="16" class="summary-row" v-loading="repairLoading">
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-label">维修工单</div>
              <div class="stat-value">{{ repairDur?.woCount ?? '-' }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-label">已完工</div>
              <div class="stat-value">{{ repairDur?.doneCount ?? '-' }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-label">平均全程时长（h）</div>
              <div class="stat-value">{{ hours(repairDur?.avgHours) }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-label">全程 P90（h）</div>
              <div class="stat-value">{{ hours(repairDur?.p90Hours) }}</div>
            </el-card>
          </el-col>
        </el-row>

        <!-- KPI：纯维修 / 响应 / 根因 -->
        <el-row :gutter="16" class="summary-row" v-loading="repairLoading">
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-label">平均纯维修时长（h）</div>
              <div class="stat-value">{{ hours(repairDur?.avgRepairHours) }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-label">平均响应时长（h）</div>
              <div class="stat-value">{{ hours(repairDur?.avgResponseHours) }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-label">响应率</div>
              <div class="stat-value">{{ ratePercent(repairDur?.respondRate) }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-label">根因填写率</div>
              <div class="stat-value">{{ ratePercent(repairDur?.rootCauseFillRate) }}</div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 趋势 -->
        <el-card shadow="never" v-loading="repairLoading" class="repair-block">
          <template #header>工单数量与平均全程时长趋势</template>
          <div ref="repairChartRef" class="chart-box"></div>
          <el-empty
            v-if="!repairLoading && repairTrend.length === 0"
            description="区间内暂无维修工单"
            :image-size="60"
          />
        </el-card>

        <el-row :gutter="16">
          <!-- 根因分布 -->
          <el-col :span="12">
            <el-card shadow="never" v-loading="repairLoading">
              <template #header>根因分布（完工 {{ repairDur?.doneCount ?? 0 }} 单，已填 {{ repairDur?.rootCauseCount ?? 0 }} 单）</template>
              <el-table :data="repairCauses" size="small" max-height="260">
                <el-table-column label="根因" min-width="160" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.label || row.key }}</template>
                </el-table-column>
                <el-table-column prop="count" label="数量（单）" width="110" align="right" />
                <el-table-column label="占完工比" width="110" align="right">
                  <template #default="{ row }">{{ causeShare(row.count) }}</template>
                </el-table-column>
              </el-table>
              <el-empty
                v-if="!repairLoading && repairCauses.length === 0"
                description="尚无已填写的根因"
                :image-size="60"
              />
            </el-card>
          </el-col>

          <!-- 按工单备件消耗 -->
          <el-col :span="12">
            <el-card shadow="never" v-loading="repairLoading">
              <template #header>按工单备件消耗（{{ consumptionHead() }}）</template>
              <el-table :data="repairMat?.byWo ?? []" size="small" max-height="260">
                <el-table-column prop="woNo" label="工单号" width="130" show-overflow-tooltip />
                <el-table-column prop="woType" label="类型" width="90" />
                <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
                <el-table-column prop="qty" label="数量" width="80" align="right" />
                <el-table-column label="金额（元）" width="120" align="right">
                  <template #default="{ row }">{{ money(row.value) }}</template>
                </el-table-column>
              </el-table>
              <el-empty
                v-if="!repairLoading && (repairMat?.byWo ?? []).length === 0"
                description="区间内无出库流水"
                :image-size="60"
              />
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16" class="repair-block">
          <!-- 按物料备件消耗 -->
          <el-col :span="12">
            <el-card shadow="never" v-loading="repairLoading">
              <template #header>按物料备件消耗</template>
              <el-table :data="repairMat?.byMaterial ?? []" size="small" max-height="260">
                <el-table-column prop="materialCode" label="物料编码" width="110" show-overflow-tooltip />
                <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
                <el-table-column prop="unit" label="单位" width="70" />
                <el-table-column prop="qty" label="数量" width="80" align="right" />
                <el-table-column prop="woCount" label="领用单数" width="90" align="right" />
                <el-table-column label="金额（元）" min-width="120" align="right">
                  <template #default="{ row }">{{ money(row.value) }}</template>
                </el-table-column>
              </el-table>
              <el-empty
                v-if="!repairLoading && (repairMat?.byMaterial ?? []).length === 0"
                description="区间内无出库流水"
                :image-size="60"
              />
            </el-card>
          </el-col>

          <!-- 维修费用四类汇总 -->
          <el-col :span="12">
            <el-card shadow="never" v-loading="repairLoading">
              <template #header>维修费用汇总（{{ repairCost?.summary.woCount ?? 0 }} 单）</template>
              <el-row :gutter="12">
                <el-col :span="6">
                  <div class="cost-item">
                    <div class="cost-label">人工费</div>
                    <div class="cost-value">{{ money(repairCost?.summary.laborValue) }}</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="cost-item">
                    <div class="cost-label">外委费</div>
                    <div class="cost-value">{{ money(repairCost?.summary.serviceValue) }}</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="cost-item">
                    <div class="cost-label">其他费</div>
                    <div class="cost-value">{{ money(repairCost?.summary.otherValue) }}</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="cost-item">
                    <div class="cost-label">备件费</div>
                    <div class="cost-value">{{ money(repairCost?.summary.partsValue) }}</div>
                  </div>
                </el-col>
              </el-row>
              <div class="cost-hint">
                费用合计 {{ costTotal }} 元；备件费由
                {{ repairCost?.summary.partsWoCount ?? 0 }} 张有出库流水的工单按「数量 × 物料单价」反算
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 按工单维修费用榜 -->
        <el-card shadow="never" v-loading="repairLoading">
          <template #header>按工单维修费用榜</template>
          <el-table :data="repairCost?.byWo ?? []" size="small" max-height="320">
            <el-table-column prop="woNo" label="工单号" width="130" show-overflow-tooltip />
            <el-table-column prop="woType" label="类型" width="90" />
            <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
            <el-table-column label="人工（元）" width="110" align="right">
              <template #default="{ row }">{{ money(row.laborValue) }}</template>
            </el-table-column>
            <el-table-column label="外委（元）" width="110" align="right">
              <template #default="{ row }">{{ money(row.serviceValue) }}</template>
            </el-table-column>
            <el-table-column label="其他（元）" width="110" align="right">
              <template #default="{ row }">{{ money(row.otherValue) }}</template>
            </el-table-column>
            <el-table-column label="备件（元）" width="110" align="right">
              <template #default="{ row }">{{ money(row.partsValue) }}</template>
            </el-table-column>
            <el-table-column label="合计（元）" min-width="120" align="right">
              <template #default="{ row }">
                <b>{{ money(row.totalValue) }}</b>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!repairLoading && (repairCost?.byWo ?? []).length === 0"
            description="区间内无维修工单"
            :image-size="60"
          />
        </el-card>
      </el-tab-pane>

      <!-- ────────── 仓储报表 ────────── -->
      <el-tab-pane label="仓储报表" name="warehouse">
        <!-- 仓库维度筛选 -->
        <div class="wh-toolbar">
          <span class="wh-toolbar-label">仓库</span>
          <el-select
            v-model="whWarehouseCode"
            clearable
            filterable
            placeholder="全部仓库"
            style="width: 220px"
            @change="loadWarehouseReport"
          >
            <el-option
              v-for="w in whWarehouseOpts"
              :key="w.warehouseCode"
              :label="`${w.warehouseCode} ${w.name}`"
              :value="w.warehouseCode"
            />
          </el-select>
          <span class="wh-toolbar-hint">筛选后仅列出在该仓有库存记录的物料</span>
        </div>
        <!-- 汇总卡 -->
        <el-row :gutter="16" class="summary-row">
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-label">物料总数</div>
              <div class="stat-value">{{ whData?.summary.materialCount ?? '-' }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-label">库存总值（元）</div>
              <div class="stat-value">{{ whData ? money(whData.summary.totalStockValue) : '-' }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-label">低库存</div>
              <div class="stat-value" :class="{ 'stat-warning': (whData?.summary.lowStockCount ?? 0) > 0 }">
                {{ whData?.summary.lowStockCount ?? '-' }}
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-label">缺货</div>
              <div class="stat-value" :class="{ 'stat-danger': (whData?.summary.outOfStockCount ?? 0) > 0 }">
                {{ whData?.summary.outOfStockCount ?? '-' }}
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-card shadow="never" v-loading="whLoading" class="wh-block">
          <template #header>按仓库分组库存</template>
          <el-table :data="byWarehouse" size="small" max-height="320">
            <el-table-column prop="warehouseCode" label="仓库编号" width="110" />
            <el-table-column prop="name" label="仓库名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="warehouseType" label="类型" width="110">
              <template #default="{ row }">{{ row.warehouseType || '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" align="center" />
            <el-table-column label="物料数" width="90" align="right">
              <template #default="{ row }">{{ row.materialCount }}</template>
            </el-table-column>
            <el-table-column label="库存数量" width="100" align="right">
              <template #default="{ row }">{{ row.stockQty }}</template>
            </el-table-column>
            <el-table-column label="库存金额（元）" min-width="130" align="right">
              <template #default="{ row }">{{ money(row.stockValue) }}</template>
            </el-table-column>
            <el-table-column label="低库存" width="90" align="right">
              <template #default="{ row }">
                <span :class="{ 'stat-warning': row.lowCount > 0 }">{{ row.lowCount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="缺货" width="90" align="right">
              <template #default="{ row }">
                <span :class="{ 'stat-danger': row.outCount > 0 }">{{ row.outCount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="库存状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.empty" type="info" size="small">仓库无货</el-tag>
                <el-tag v-else type="success" size="small">有货</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!whLoading && byWarehouse.length === 0"
            description="暂无仓库数据"
            :image-size="60"
          />
        </el-card>

        <el-card shadow="never" v-loading="whLoading" class="wh-block">
          <template #header>仓库无货（{{ emptyWarehouses.length }}）</template>
          <el-table :data="emptyWarehouses" size="small" max-height="220">
            <el-table-column prop="warehouseCode" label="仓库编号" width="110" />
            <el-table-column prop="name" label="仓库名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="warehouseType" label="类型" width="120">
              <template #default="{ row }">{{ row.warehouseType || '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" align="center" />
            <el-table-column label="说明" min-width="180">
              <template #default="{ row }">
                {{ `${row.warehouseCode} ${warehouseName(row.warehouseCode)} 无任何分仓库存记录` }}
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!whLoading && emptyWarehouses.length === 0"
            description="所有仓库均有库存"
            :image-size="60"
          />
        </el-card>

        <el-card shadow="never" v-loading="whLoading">
          <template #header>物料库存明细</template>
          <el-table
            :data="whData?.items ?? []"
            size="small"
            max-height="460"
            :row-class-name="rowClass"
          >
            <el-table-column prop="materialCode" label="物料编码" min-width="110" show-overflow-tooltip />
            <el-table-column prop="name" label="物料名称" min-width="110" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="90">
              <template #default="{ row }">{{ row.category || '-' }}</template>
            </el-table-column>
            <el-table-column label="默认仓库" width="140" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.warehouseCode ? `${row.warehouseCode} ${warehouseName(row.warehouseCode)}` : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="70">
              <template #default="{ row }">{{ row.unit || '-' }}</template>
            </el-table-column>
            <el-table-column prop="stock" label="库存" width="80" align="right" />
            <el-table-column prop="safetyStock" label="安全库存" width="90" align="right" />
            <el-table-column prop="maxStock" label="最大库存" width="90" align="right" />
            <el-table-column prop="totalIn" label="累计入库" width="90" align="right" />
            <el-table-column prop="totalOut" label="累计出库" width="90" align="right" />
            <el-table-column label="库存金额（元）" min-width="120" align="right">
              <template #default="{ row }">{{ money(row.stockValue) }}</template>
            </el-table-column>
            <el-table-column label="预警" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.alert" :type="alertTagType(row.alert)" size="small" effect="dark">
                  {{ alertText(row.alert) }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!whLoading && (whData?.items ?? []).length === 0"
            description="暂无物料数据"
            :image-size="60"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.toolbar-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.toolbar-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.repair-block {
  margin-top: 16px;
}

.cost-item {
  text-align: center;
  padding: 6px 0;
}

.cost-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.cost-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.5;
}

.cost-hint {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.chart-box {
  height: 380px;
  width: 100%;
}

.stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.4;
}

.stat-danger {
  color: var(--el-color-danger);
}

.stat-warning {
  color: var(--el-color-warning);
}

.summary-row {
  margin-bottom: 16px;
}

.sla-card {
  margin-top: 16px;
}

.sla-item {
  text-align: center;
}

.sla-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.sla-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.sla-compliance {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.sla-compliance b {
  color: var(--el-color-primary);
  font-size: 16px;
}

:deep(.row-danger) {
  --el-table-tr-bg-color: var(--el-color-danger-light-9);
}

:deep(.row-warning) {
  --el-table-tr-bg-color: var(--el-color-warning-light-9);
}

.wh-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.wh-toolbar-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.wh-toolbar-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.wh-block {
  margin-bottom: 16px;
}
</style>
