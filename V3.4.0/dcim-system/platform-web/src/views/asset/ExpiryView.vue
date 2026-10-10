<script setup lang="ts">
/** 到期提醒：资产保修与合同到期清单（含已过保在用）、提醒节点说明、手动触发提醒 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { fetchExpiry, runExpiry } from '@/api/capacity'
import type { ExpirySummary } from '@/api/capacity'

const auth = useAuthStore()
const router = useRouter()
const isAdmin = computed(() => !!auth.user?.isAdmin)
const days = ref(90)
const tab = ref('warranty')
const warranty = ref<any[]>([])
const contract = ref<any[]>([])
const summary = ref<ExpirySummary | null>(null)
const loading = ref(false)
const showExpired = ref(false)

const warrantyRows = computed(() => warranty.value.filter((r) => showExpired.value || r.daysLeft >= 0))
const contractRows = computed(() => contract.value.filter((r) => showExpired.value || r.daysLeft >= 0))

function tagType(d: number) {
  if (d < 0) return 'info'
  if (d <= 7) return 'danger'
  if (d <= 30) return 'warning'
  return 'primary'
}
function leftText(d: number) {
  return d < 0 ? `已过期 ${-d} 天` : d === 0 ? '今天到期' : `剩 ${d} 天`
}

async function load() {
  loading.value = true
  try {
    const r = await fetchExpiry(days.value)
    warranty.value = r.warranty || []
    contract.value = r.contract || []
    summary.value = r.summary
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

async function doRun() {
  try {
    const r = await runExpiry()
    ElMessage.success(`已执行：保修 ${r.warranty} 条、合同 ${r.contract} 条新提醒（已提醒过的节点不会重复发送）`)
  } catch {
    // ignore
  }
}

function exportCsv() {
  const rows = tab.value === 'warranty' ? warrantyRows.value : contractRows.value
  const head = tab.value === 'warranty'
    ? ['资产编号', '名称', '型号', 'SN', '状态', '机房', '机柜', '保修到期', '剩余天数', '合同号']
    : ['合同编号', '名称', '类型', '相对方', '金额', '到期日', '剩余天数', '关联资产数']
  const keys = tab.value === 'warranty'
    ? ['assetNo', 'name', 'modelCode', 'sn', 'statusBiz', 'roomCode', 'rackCode', 'endDate', 'daysLeft', 'contractNo']
    : ['contractNo', 'name', 'type', 'party', 'amount', 'endDate', 'daysLeft', 'assetCount']
  const esc = (v: unknown) => {
    let s = v == null ? '' : String(v)
    if (/^[=+\-@]/.test(s)) s = "'" + s // 防 CSV 公式注入
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const csv = '﻿' + [head.join(','), ...rows.map((r) => keys.map((k) => esc(r[k])).join(','))].join('\n')
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  a.download = `${tab.value === 'warranty' ? '保修到期' : '合同到期'}-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

onMounted(load)
</script>

<template>
  <div class="page-card">
    <div class="page-title">到期提醒</div>
    <div class="cards">
      <div class="card">
        <div class="num c-danger">{{ summary?.outOfWarrantyInUse ?? '-' }}</div>
        <div class="lbl">已过保仍在用</div>
      </div>
      <div class="card">
        <div class="num c-warning">{{ summary?.warranty30 ?? '-' }}</div>
        <div class="lbl">30 天内过保</div>
      </div>
      <div class="card">
        <div class="num">{{ summary?.warranty90 ?? '-' }}</div>
        <div class="lbl">90 天内过保</div>
      </div>
      <div class="card">
        <div class="num">{{ summary?.warrantyUnknown ?? '-' }}</div>
        <div class="lbl">在用但未填保修日期</div>
      </div>
      <div class="card">
        <div class="num c-warning">{{ summary?.contract30 ?? '-' }}</div>
        <div class="lbl">30 天内到期合同</div>
      </div>
      <div class="card">
        <div class="num c-danger">{{ summary?.contractExpiredActive ?? '-' }}</div>
        <div class="lbl">已过期仍「生效」的合同</div>
      </div>
    </div>

    <div class="page-toolbar">
      <span>未来</span>
      <el-select v-model="days" style="width: 110px" @change="load">
        <el-option :value="30" label="30 天" />
        <el-option :value="90" label="90 天" />
        <el-option :value="180" label="180 天" />
        <el-option :value="365" label="1 年" />
      </el-select>
      <span>内到期</span>
      <el-checkbox v-model="showExpired">含近一年已过期</el-checkbox>
      <div style="flex: 1"></div>
      <el-button @click="exportCsv">导出 CSV</el-button>
      <el-button v-if="isAdmin" @click="doRun">立即发送提醒</el-button>
    </div>
    <el-alert type="info" :closable="false" style="margin-bottom: 10px">
      每天 08:50 自动按提醒节点（保修默认 60/30/7 天、合同 90/30/7 天及当天）通知「资产管理专员」角色，每个节点只提醒一次；
      订阅 Webhook 事件「资产保修即将到期 / 合同即将到期」可推送到企微/钉钉/飞书群。节点与通知角色在「容量管理 → 参数」中修改。
    </el-alert>

    <el-tabs v-model="tab">
      <el-tab-pane :label="`保修到期（${warrantyRows.length}）`" name="warranty">
        <el-table v-loading="loading" :data="warrantyRows" border stripe size="small" max-height="560">
          <el-table-column label="剩余" width="110">
            <template #default="{ row }"><el-tag :type="tagType(row.daysLeft)" size="small">{{ leftText(row.daysLeft) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="endDate" label="保修到期" width="110" />
          <el-table-column label="资产" min-width="200">
            <template #default="{ row }">
              <el-link type="primary" @click="router.push({ path: '/assets/inventory', query: { keyword: row.assetNo } })">{{ row.assetNo }}</el-link>
              <div class="sub">{{ row.name }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="modelCode" label="型号" width="140" show-overflow-tooltip />
          <el-table-column prop="sn" label="SN" width="150" show-overflow-tooltip />
          <el-table-column prop="statusBiz" label="状态" width="100" />
          <el-table-column label="位置" width="160">
            <template #default="{ row }">{{ [row.roomCode, row.rackCode].filter(Boolean).join(' / ') || '-' }}</template>
          </el-table-column>
          <el-table-column prop="contractNo" label="合同" width="140" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane :label="`合同到期（${contractRows.length}）`" name="contract">
        <el-table v-loading="loading" :data="contractRows" border stripe size="small" max-height="560">
          <el-table-column label="剩余" width="110">
            <template #default="{ row }"><el-tag :type="tagType(row.daysLeft)" size="small">{{ leftText(row.daysLeft) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="endDate" label="到期日" width="110" />
          <el-table-column label="合同" min-width="220">
            <template #default="{ row }">{{ row.contractNo }}<div class="sub">{{ row.name }}</div></template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="90" />
          <el-table-column prop="party" label="相对方" min-width="160" show-overflow-tooltip />
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">{{ row.amount != null ? Number(row.amount).toLocaleString() : '-' }}</template>
          </el-table-column>
          <el-table-column prop="assetCount" label="关联资产" width="90" align="right" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px 14px;
}
.num {
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.lbl,
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.c-danger {
  color: var(--el-color-danger);
}
.c-warning {
  color: var(--el-color-warning);
}
</style>
