<script setup lang="ts">
/**
 * 成本分摊：按月折旧额归集到部门 / 区域 / 机房 / 分类 / 责任人，含占比与近 12 个月趋势。
 */
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import echarts, { CHART_COLORS } from '@/utils/echarts'
import type { EChartsType } from '@/utils/echarts'
import { fetchDepreciationPeriods } from '@/api/finance'
import { fetchCostAllocation } from '@/api/upgrade'
import type { CostAllocationResult } from '@/api/upgrade'
import { escapeHtml } from '@/utils/escape'

const DIMS = [
  { value: 'department', label: '部门' },
  { value: 'region', label: '区域' },
  { value: 'room', label: '机房' },
  { value: 'category', label: '资产分类' },
  { value: 'owner', label: '责任人' },
]

const periods = ref<string[]>([])
const period = ref('')
const dim = ref('department')
const data = ref<CostAllocationResult | null>(null)
const loading = ref(false)
const pieEl = ref<HTMLDivElement>()
const trendEl = ref<HTMLDivElement>()
let pie: EChartsType | null = null
let trend: EChartsType | null = null

function money(v?: number) {
  return v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load() {
  loading.value = true
  try {
    data.value = await fetchCostAllocation({ period: period.value || undefined, dim: dim.value })
    if (!period.value) period.value = data.value.period
    await nextTick()
    render()
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

function render() {
  const d = data.value
  if (!d) return
  if (pieEl.value) {
    pie = pie || echarts.init(pieEl.value)
    pie.setOption(
      {
        color: CHART_COLORS,
        tooltip: { trigger: 'item', formatter: (p: any) => `${escapeHtml(p.name)}<br/>¥${money(p.value)}（${p.percent}%）` },
        series: [{ type: 'pie', radius: ['40%', '70%'], data: d.rows.map((r) => ({ name: r.key, value: Number(r.monthDep) })) }],
      },
      true,
    )
  }
  if (trendEl.value) {
    trend = trend || echarts.init(trendEl.value)
    trend.setOption(
      {
        color: CHART_COLORS,
        grid: { left: 64, right: 16, top: 16, bottom: 28 },
        tooltip: { trigger: 'axis', valueFormatter: (v: any) => `¥${money(v)}` },
        xAxis: { type: 'category', data: d.trend.map((t) => t.period) },
        yAxis: { type: 'value' },
        series: [{ type: 'line', smooth: true, areaStyle: { opacity: 0.12 }, data: d.trend.map((t) => Number(t.monthDep)) }],
      },
      true,
    )
  }
}

function exportCsv() {
  const d = data.value
  if (!d) return
  const label = DIMS.find((x) => x.value === dim.value)?.label || dim.value
  const lines = [[label, '资产数', '当月折旧', '占比%', '原值', '净值'].join(',')]
  for (const r of d.rows) {
    const key = /^[=+\-@]/.test(r.key) ? `'${r.key}` : r.key
    lines.push([`"${key.replace(/"/g, '""')}"`, r.assetCount, r.monthDep, r.share, r.originValue, r.netValue].join(','))
  }
  const blob = new Blob(['﻿' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `成本分摊_${label}_${d.period}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

function onResize() {
  pie?.resize()
  trend?.resize()
}

onMounted(async () => {
  window.addEventListener('resize', onResize)
  try {
    periods.value = await fetchDepreciationPeriods()
  } catch {
    // ignore
  }
  await load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  pie?.dispose()
  trend?.dispose()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">成本分摊</div>
    <div class="page-toolbar">
      <span>期间</span>
      <el-select v-model="period" style="width: 130px" filterable allow-create @change="load">
        <el-option v-for="p in periods" :key="p" :label="p.slice(0, 7)" :value="p.slice(0, 7)" />
      </el-select>
      <span>分摊维度</span>
      <el-radio-group v-model="dim" @change="load">
        <el-radio-button v-for="d in DIMS" :key="d.value" :value="d.value">{{ d.label }}</el-radio-button>
      </el-radio-group>
      <div style="flex: 1"></div>
      <el-button :disabled="!data?.rows.length" @click="exportCsv">导出 CSV</el-button>
    </div>

    <div v-loading="loading">
      <el-empty v-if="data && !data.rows.length" description="该期间尚无折旧计提数据（请先在「折旧底稿」执行计提）" />
      <template v-else-if="data">
        <el-row :gutter="16">
          <el-col :span="9">
            <div class="total">
              <div class="lbl">{{ data.period }} 当月折旧合计</div>
              <div class="num">¥{{ money(data.total) }}</div>
            </div>
            <div ref="pieEl" class="chart"></div>
          </el-col>
          <el-col :span="15">
            <div class="lbl" style="margin-bottom: 6px">近 12 个月折旧趋势</div>
            <div ref="trendEl" class="chart" style="height: 320px"></div>
          </el-col>
        </el-row>
        <el-table :data="data.rows" border stripe show-summary style="margin-top: 12px">
          <el-table-column prop="key" :label="DIMS.find((x) => x.value === dim)?.label" min-width="160" />
          <el-table-column prop="assetCount" label="资产数" width="100" align="right" />
          <el-table-column label="当月折旧" width="160" align="right">
            <template #default="{ row }">{{ money(row.monthDep) }}</template>
          </el-table-column>
          <el-table-column label="占比" width="180">
            <template #default="{ row }">
              <el-progress :percentage="Number(row.share)" :stroke-width="10" />
            </template>
          </el-table-column>
          <el-table-column label="原值" width="160" align="right">
            <template #default="{ row }">{{ money(row.originValue) }}</template>
          </el-table-column>
          <el-table-column label="净值" width="160" align="right">
            <template #default="{ row }">{{ money(row.netValue) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </div>
  </div>
</template>

<style scoped>
.total {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px 14px;
}
.num {
  font-size: 24px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.lbl {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.chart {
  height: 260px;
}
</style>
