<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  GPU_HEALTH_LEVELS,
  fetchGpuCardSeries,
  fetchGpuCards,
  fetchGpuHealthConfig,
  scoreGpuCardsNow,
  updateGpuHealthConfig,
} from '@/api/gpu'
import type { GpuCardItem } from '@/api/gpu'
import { useAuthStore } from '@/stores/auth'
import echarts from '@/utils/echarts'

const auth = useAuthStore()
const canWrite = () => auth.canWrite('gpu')

/* ---------------- 列表查询 ---------------- */
const query = reactive({ page: 1, size: 20, hostAssetNo: '', level: '', keyword: '' })
const total = ref(0)
const records = ref<GpuCardItem[]>([])
const listLoading = ref(false)
const scoring = ref(false)

async function load() {
  listLoading.value = true
  try {
    const res = await fetchGpuCards({
      page: query.page,
      size: query.size,
      hostAssetNo: query.hostAssetNo || undefined,
      level: query.level || undefined,
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

async function scoreNow() {
  scoring.value = true
  try {
    const created = await scoreGpuCardsNow()
    ElMessage.success(`评分完成，本轮高危自动建单 ${created} 条`)
    void load()
  } catch {
    // 拦截器已提示
  } finally {
    scoring.value = false
  }
}

const levelTag = (lv?: string) =>
  lv === '高危' ? 'danger' : lv === '劣化' ? 'warning' : lv === '观察' ? 'warning' : lv === '良' ? 'success' : lv === '优' ? 'success' : 'info'
const scoreColor = (score?: number) =>
  score == null ? '#909399' : score < 40 ? '#f56c6c' : score < 60 ? '#e6a23c' : score < 75 ? '#e6a23c' : '#67c23a'

/* ---------------- 趋势弹窗 ---------------- */
const trendVisible = ref(false)
const trendCard = ref<GpuCardItem | null>(null)
const trendLoading = ref(false)
const chartEl = ref<HTMLDivElement>()
let chart: ReturnType<typeof echarts.init> | null = null

async function openTrend(row: GpuCardItem) {
  trendCard.value = row
  trendVisible.value = true
  trendLoading.value = true
  try {
    const points = await fetchGpuCardSeries(row.cardNo)
    await nextTick()
    if (!chartEl.value) return
    chart?.dispose()
    chart = echarts.init(chartEl.value)
    const ts = points.map((p) => p.ts?.replace('T', ' ').slice(5, 16) ?? '')
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['温度(℃)', '功耗(W)', '利用率(%)'] },
      grid: { left: 48, right: 16, top: 40, bottom: 28 },
      xAxis: { type: 'category', data: ts },
      yAxis: [
        { type: 'value', name: '℃ / W' },
        { type: 'value', name: '%', max: 100, splitLine: { show: false } },
      ],
      series: [
        { name: '温度(℃)', type: 'line', showSymbol: false, data: points.map((p) => p.tempC ?? null) },
        { name: '功耗(W)', type: 'line', showSymbol: false, data: points.map((p) => p.powerW ?? null) },
        { name: '利用率(%)', type: 'line', yAxisIndex: 1, showSymbol: false, data: points.map((p) => p.utilPct ?? null) },
      ],
    })
    chart.resize()
  } catch {
    // 拦截器已提示
  } finally {
    trendLoading.value = false
  }
}

function onResize() {
  chart?.resize()
}

onMounted(() => {
  void load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})

/* ---------------- 权重配置 ---------------- */
const configVisible = ref(false)
const configSaving = ref(false)
const cfg = reactive({ weightEcc: 40, weightTemp: 25, weightXid: 20, weightPower: 15, tempLimitC: 85 })
const cfgSum = computed(() => cfg.weightEcc + cfg.weightTemp + cfg.weightXid + cfg.weightPower)

async function openConfig() {
  try {
    Object.assign(cfg, await fetchGpuHealthConfig())
  } catch {
    // 拦截器已提示
  }
  configVisible.value = true
}

async function saveConfig() {
  if (cfgSum.value !== 100) {
    ElMessage.warning('四项权重之和必须等于 100')
    return
  }
  configSaving.value = true
  try {
    await updateGpuHealthConfig({ ...cfg })
    ElMessage.success('评分权重已更新')
    configVisible.value = false
  } catch {
    // 拦截器已提示（温度阈值越界 400）
  } finally {
    configSaving.value = false
  }
}
</script>

<template>
  <div class="page-card">
    <div class="page-title">GPU 卡档案与健康度</div>

    <!-- 顶部筛选 -->
    <div class="page-toolbar">
      <el-input
        v-model="query.hostAssetNo"
        placeholder="宿主机资产号"
        clearable
        style="width: 190px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="query.level" placeholder="健康档位" clearable style="width: 120px" @change="search">
        <el-option v-for="lv in GPU_HEALTH_LEVELS" :key="lv" :label="lv" :value="lv" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="卡号 / GPU SN / 型号"
        clearable
        style="width: 190px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" plain @click="search">查询</el-button>
      <div style="flex: 1"></div>
      <el-button @click="openConfig">评分权重</el-button>
      <el-button v-if="canWrite()" type="primary" :loading="scoring" @click="scoreNow">立即评分</el-button>
    </div>

    <!-- 列表 -->
    <el-table v-loading="listLoading" :data="records" border stripe>
      <el-table-column prop="cardNo" label="卡号" min-width="210" fixed="left" show-overflow-tooltip />
      <el-table-column prop="hostAssetNo" label="宿主机" min-width="170" show-overflow-tooltip />
      <el-table-column prop="slot" label="槽位" width="65" />
      <el-table-column prop="gpuSn" label="GPU SN" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.gpuSn || '-' }}
          <el-tooltip v-if="row.prevSn" :content="`换卡前：${row.prevSn}`">
            <el-tag type="warning" size="small" style="margin-left:4px">换卡</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="型号 / 显存" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.model || '-' }}{{ row.memoryGb ? ` / ${row.memoryGb}G` : '' }}</template>
      </el-table-column>
      <el-table-column label="健康分" width="90">
        <template #default="{ row }">
          <span :style="{ color: scoreColor(row.healthScore), fontWeight: 600 }">{{ row.healthScore ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="healthLevel" label="档位" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.healthLevel" :type="levelTag(row.healthLevel)" size="small">{{ row.healthLevel }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="fw" label="固件" min-width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fw || '-' }}</template>
      </el-table-column>
      <el-table-column prop="lastScoredAt" label="最近评分" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.lastScoredAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openTrend(row)">趋势</el-button>
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

    <!-- 趋势弹窗 -->
    <el-dialog
      v-model="trendVisible"
      :title="`指标趋势 — ${trendCard?.cardNo || ''}`"
      width="860px"
      destroy-on-close
      @closed="() => { chart?.dispose(); chart = null }"
    >
      <div v-if="trendCard" style="margin-bottom: 8px; color: #606266; font-size: 13px">
        SN：{{ trendCard.gpuSn || '-' }}　型号：{{ trendCard.model || '-' }}　
        健康分：<span :style="{ color: scoreColor(trendCard.healthScore), fontWeight: 600 }">{{ trendCard.healthScore ?? '-' }}</span>
        <el-tag v-if="trendCard.healthLevel" :type="levelTag(trendCard.healthLevel)" size="small" style="margin-left: 6px">
          {{ trendCard.healthLevel }}
        </el-tag>
      </div>
      <div v-loading="trendLoading" style="height: 60px" v-if="trendLoading"></div>
      <div ref="chartEl" style="width: 100%; height: 360px"></div>
    </el-dialog>

    <!-- 权重配置 -->
    <el-dialog v-model="configVisible" title="健康度评分权重（四项和=100）" width="520px" destroy-on-close>
      <el-form label-width="150px">
        <el-form-item label="ECC 双位错误权重">
          <el-input-number v-model="cfg.weightEcc" :min="0" :max="100" controls-position="right" />
        </el-form-item>
        <el-form-item label="温度越限权重">
          <el-input-number v-model="cfg.weightTemp" :min="0" :max="100" controls-position="right" />
        </el-form-item>
        <el-form-item label="XID 错误权重">
          <el-input-number v-model="cfg.weightXid" :min="0" :max="100" controls-position="right" />
        </el-form-item>
        <el-form-item label="功耗波动权重">
          <el-input-number v-model="cfg.weightPower" :min="0" :max="100" controls-position="right" />
        </el-form-item>
        <el-form-item label="温度阈值(℃)">
          <el-input-number v-model="cfg.tempLimitC" :min="40" :max="120" controls-position="right" />
        </el-form-item>
        <el-alert
          :type="cfgSum === 100 ? 'success' : 'error'"
          :closable="false"
          :title="`当前四项权重之和：${cfgSum}${cfgSum === 100 ? '' : '（必须等于 100）'}`"
        />
      </el-form>
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" :loading="configSaving" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
