<script setup lang="ts">
/**
 * 库存水位：缺货 / 低库存 / 超储一览，近 30 天日均消耗、可用天数与补货建议
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { fetchWarehouseOptions } from '@/api/warehouse'
import type { WarehouseOption } from '@/api/warehouse'
import { STOCK_LEVEL_LABEL, fetchStockLevelSummary, fetchStockLevels } from '@/api/upgrade'
import type { StockLevelRow } from '@/api/upgrade'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const canStockIn = computed(() => auth.canWrite('stockLogs'))

const LEVEL_TAG: Record<string, 'danger' | 'warning' | 'success' | 'info' | 'primary'> = {
  OUT: 'danger',
  LOW: 'warning',
  OVER: 'primary',
  NORMAL: 'success',
  UNSET: 'info',
}

const query = reactive({ page: 1, size: 20, keyword: '', level: 'ALERT', warehouseCode: '' })
const records = ref<StockLevelRow[]>([])
const total = ref(0)
const loading = ref(false)
const summary = ref<Record<string, number>>({})
const warehouses = ref<WarehouseOption[]>([])

async function load() {
  loading.value = true
  try {
    const res = await fetchStockLevels({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      level: query.level || undefined,
      warehouseCode: query.warehouseCode || undefined,
    })
    records.value = res.records ?? []
    total.value = res.total ?? 0
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  try {
    summary.value = (await fetchStockLevelSummary()) as unknown as Record<string, number>
  } catch {
    // ignore
  }
}

function pick(level: string) {
  query.level = query.level === level ? '' : level
  search()
}

function search() {
  query.page = 1
  void load()
}

function percent(r: StockLevelRow): number {
  const cap = r.maxStock > 0 ? r.maxStock : Math.max(r.safetyStock * 2, r.stock, 1)
  return Math.min(100, Math.round((r.stock / cap) * 100))
}

function barColor(r: StockLevelRow): string {
  if (r.level === 'OUT') return '#f56c6c'
  if (r.level === 'LOW') return '#e6a23c'
  if (r.level === 'OVER') return '#409eff'
  return '#67c23a'
}

function money(v?: number) {
  return v == null ? '-' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

function goStockIn(r: StockLevelRow) {
  void router.push({ path: '/rfid/scan-in', query: { material: r.materialCode, qty: String(r.suggestQty || '') } })
}

onMounted(async () => {
  try {
    warehouses.value = await fetchWarehouseOptions()
  } catch {
    // ignore
  }
  if (typeof route.query.keyword === 'string') {
    query.keyword = route.query.keyword
    query.level = ''
  }
  await Promise.all([load(), loadSummary()])
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">库存水位</div>

    <div class="cards">
      <div
        v-for="lv in ['OUT', 'LOW', 'OVER', 'NORMAL', 'UNSET']"
        :key="lv"
        class="card"
        :class="{ active: query.level === lv || (query.level === 'ALERT' && (lv === 'OUT' || lv === 'LOW')) }"
        @click="pick(lv)"
      >
        <div class="num" :class="'lv-' + lv">{{ summary[lv] ?? 0 }}</div>
        <div class="lbl">{{ STOCK_LEVEL_LABEL[lv] }}</div>
      </div>
      <div class="card static">
        <div class="num">¥{{ money(summary.stockValue) }}</div>
        <div class="lbl">库存金额（按参考单价）</div>
      </div>
    </div>

    <div class="page-toolbar">
      <el-input v-model="query.keyword" placeholder="物料编码 / 名称 / 型号" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.level" placeholder="水位" clearable style="width: 160px" @change="search">
        <el-option label="需要关注（缺货+低库存）" value="ALERT" />
        <el-option v-for="(l, k) in STOCK_LEVEL_LABEL" :key="k" :label="l" :value="k" />
      </el-select>
      <el-select v-model="query.warehouseCode" placeholder="仓库" clearable style="width: 160px" @change="search">
        <el-option v-for="w in warehouses" :key="w.warehouseCode" :label="w.name" :value="w.warehouseCode" />
      </el-select>
      <el-button type="primary" plain @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe row-key="materialCode">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="wh">
            <span v-if="!row.warehouses.length" class="sub">各仓均无库存</span>
            <el-tag v-for="w in row.warehouses" :key="w.warehouseCode" effect="plain" style="margin-right: 8px">
              {{ w.warehouseName || w.warehouseCode }}：{{ w.qty }} {{ row.unit }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="materialCode" label="物料编码" width="140" />
      <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="model" label="型号" min-width="100" show-overflow-tooltip />
      <el-table-column label="水位" width="120">
        <template #default="{ row }">
          <el-tag :type="LEVEL_TAG[row.level]" size="small">{{ STOCK_LEVEL_LABEL[row.level] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="库存 / 安全 / 最高" min-width="230">
        <template #default="{ row }">
          <div class="bar-line">
            <el-progress :percentage="percent(row)" :color="barColor(row)" :show-text="false" :stroke-width="8" style="flex: 1" />
            <span class="nums">{{ row.stock }} / {{ row.safetyStock || '-' }} / {{ row.maxStock || '-' }} {{ row.unit }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="日均出库(30天)" width="120" align="right">
        <template #default="{ row }">{{ row.dailyOut }}</template>
      </el-table-column>
      <el-table-column label="可用天数" width="100" align="right">
        <template #default="{ row }">
          <span v-if="row.daysOfCover == null" class="sub">无消耗</span>
          <span v-else :style="{ color: row.daysOfCover < 7 ? 'var(--el-color-danger)' : '' }">{{ row.daysOfCover }} 天</span>
        </template>
      </el-table-column>
      <el-table-column label="建议补货" width="96" align="right">
        <template #default="{ row }">
          <b v-if="row.suggestQty > 0">{{ row.suggestQty }}</b>
          <span v-else class="sub">-</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canStockIn" label="操作" width="80" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="goStockIn(row)">入库</el-button>
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
  transition: border-color 0.15s;
}
.card:hover,
.card.active {
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
.lv-OUT {
  color: var(--el-color-danger);
}
.lv-LOW {
  color: var(--el-color-warning);
}
.lv-OVER {
  color: var(--el-color-primary);
}
.lv-NORMAL {
  color: var(--el-color-success);
}
.lbl {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.bar-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.nums {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  font-size: 12px;
}
.sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.wh {
  padding: 4px 12px;
}
</style>
