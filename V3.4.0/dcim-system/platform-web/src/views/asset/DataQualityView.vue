<script setup lang="ts">
/** CMDB 数据质量看板：综合评分、分维度合格率、逐条规则与不合格资产下钻（可导出整改清单） */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchQuality, fetchQualityAssets } from '@/api/platform'
import type { QualityRule, QualitySummary } from '@/api/platform'

const router = useRouter()
const data = ref<QualitySummary | null>(null)
const loading = ref(false)

const scoreColor = computed(() => {
  const s = data.value?.score ?? 0
  return s >= 90 ? '#67c23a' : s >= 75 ? '#e6a23c' : '#f56c6c'
})
const sortedRules = computed(() => [...(data.value?.rules || [])].sort((a, b) => b.failed * b.weight - a.failed * a.weight))

function rateColor(p: number) {
  return p >= 95 ? '#67c23a' : p >= 80 ? '#e6a23c' : '#f56c6c'
}

async function load() {
  loading.value = true
  try {
    data.value = await fetchQuality()
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

const drawer = ref(false)
const rule = ref<QualityRule | null>(null)
const rows = ref<any[]>([])
const total = ref(0)
const page = ref(1)
async function openRule(r: QualityRule) {
  rule.value = r
  page.value = 1
  drawer.value = true
  await loadRows()
}
async function loadRows() {
  if (!rule.value) return
  try {
    const r = await fetchQualityAssets(rule.value.id, page.value, 50)
    rows.value = r.records
    total.value = r.total
  } catch {
    rows.value = []
  }
}
async function exportCsv() {
  if (!rule.value) return
  const r = await fetchQualityAssets(rule.value.id, 1, 1000)
  const head = ['资产编号', '名称', 'SN', '型号', '分类', '状态', '机房', '机柜', 'U位', '业务IP', '管理IP', '部门', '保修到期']
  const keys = ['assetNo', 'name', 'sn', 'modelCode', 'categoryCode', 'statusBiz', 'roomCode', 'rackCode', 'unitNo', 'bizIp', 'mgmtIp', 'department', 'warrantyEnd']
  const esc = (v: unknown) => {
    let s = v == null ? '' : String(v)
    if (/^[=+\-@]/.test(s)) s = "'" + s
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const csv = '﻿' + [head.join(','), ...r.records.map((x) => keys.map((k) => esc(x[k])).join(','))].join('\n')
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  a.download = `数据质量-${rule.value.name}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

onMounted(load)
</script>

<template>
  <div class="page-card" v-loading="loading">
    <div class="page-title">
      数据质量
      <el-button style="float: right" :icon="'Refresh'" @click="load">重新评估</el-button>
    </div>
    <template v-if="data">
      <div class="top">
        <div class="score">
          <el-progress type="dashboard" :percentage="data.score" :color="scoreColor" :width="150" :stroke-width="12">
            <template #default>
              <div class="sv" :style="{ color: scoreColor }">{{ data.score }}</div>
              <div class="sl">综合评分</div>
            </template>
          </el-progress>
          <div class="sub">评估资产 {{ data.total }} 台（不含报废）</div>
        </div>
        <div class="dims">
          <div v-for="(v, k) in data.dimensions" :key="k" class="dim">
            <div class="dl">{{ k }}</div>
            <el-progress :percentage="v" :color="rateColor(v)" :stroke-width="12" />
          </div>
          <div class="sub" style="margin-top: 8px">
            完整性：关键字段是否填写；准确性：取值是否合法、是否冲突；一致性：关联数据是否互相吻合。按权重加权计算。
          </div>
        </div>
        <div class="dept">
          <div class="dl">问题最多的部门</div>
          <el-table :data="data.byDepartment" size="small" :show-header="false" max-height="190">
            <el-table-column prop="department" />
            <el-table-column width="110" align="right">
              <template #default="{ row }"><span class="c-danger">{{ row.problems }}</span> / {{ row.total }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <el-table :data="sortedRules" border size="small" style="margin-top: 16px">
        <el-table-column label="规则" min-width="200">
          <template #default="{ row }">
            <b>{{ row.name }}</b>
            <div class="sub">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="dimension" label="维度" width="80" />
        <el-table-column label="权重" width="70" align="center">
          <template #default="{ row }">{{ '★'.repeat(row.weight) }}</template>
        </el-table-column>
        <el-table-column label="合格率" min-width="200">
          <template #default="{ row }">
            <el-progress :percentage="row.passRate" :color="rateColor(row.passRate)" :stroke-width="10" />
          </template>
        </el-table-column>
        <el-table-column label="不合格 / 适用" width="130" align="right">
          <template #default="{ row }">
            <span :class="{ 'c-danger': row.failed > 0 }">{{ row.failed }}</span> / {{ row.scope }}
          </template>
        </el-table-column>
        <el-table-column width="90">
          <template #default="{ row }">
            <el-button v-if="row.failed" link type="primary" @click="openRule(row)">查看清单</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <el-drawer v-model="drawer" size="900px" :title="`${rule?.name}（${total}）`">
      <div class="page-toolbar">
        <span class="sub">{{ rule?.description }}</span>
        <div style="flex: 1"></div>
        <el-button size="small" @click="exportCsv">导出整改清单</el-button>
      </div>
      <el-table :data="rows" size="small" border max-height="640">
        <el-table-column label="资产" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push({ path: '/assets/inventory', query: { keyword: row.assetNo } })">{{ row.assetNo }}</el-link>
            <div class="sub">{{ row.name }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="sn" label="SN" width="130" show-overflow-tooltip />
        <el-table-column prop="modelCode" label="型号" width="110" show-overflow-tooltip />
        <el-table-column prop="statusBiz" label="状态" width="90" />
        <el-table-column label="位置" width="150">
          <template #default="{ row }">{{ [row.roomCode, row.rackCode, row.unitNo ? 'U' + row.unitNo : ''].filter(Boolean).join(' / ') || '-' }}</template>
        </el-table-column>
        <el-table-column label="IP" width="150">
          <template #default="{ row }">{{ row.bizIp || '-' }}<div class="sub">{{ row.mgmtIp }}</div></template>
        </el-table-column>
        <el-table-column prop="department" label="部门" width="100" />
      </el-table>
      <div class="page-pagination">
        <el-pagination v-model:current-page="page" :page-size="50" :total="total" layout="total, prev, pager, next" @current-change="loadRows" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.top {
  display: grid;
  grid-template-columns: 220px 1fr 300px;
  gap: 24px;
  align-items: start;
}
@media (max-width: 1100px) {
  .top {
    grid-template-columns: 1fr;
  }
}
.score {
  text-align: center;
}
.sv {
  font-size: 34px;
  font-weight: 700;
}
.sl {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.dim {
  margin-bottom: 14px;
}
.dl {
  font-size: 13px;
  margin-bottom: 4px;
  font-weight: 600;
}
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.c-danger {
  color: var(--el-color-danger);
}
</style>
