<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableColumnCtx } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { saveBlob } from '@/api/asset'
import {
  exportDepreciation,
  fetchDepreciation,
  fetchDepreciationPeriods,
  runDepreciation,
} from '@/api/finance'
import type { DepreciationRecord } from '@/api/finance'

const auth = useAuthStore()
const canWrite = computed(() => !!auth.modules['finance']?.write)

const SUM_PROPS = ['openingNetValue', 'depAmount', 'accumulatedDep', 'closingNetValue']

/** 金额格式化（千分位 + 两位小数；空按 0 汇总、显示 -） */
function fmtMoney(v?: number | null): string {
  if (v === null || v === undefined) return '-'
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function currentPeriod(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/* ---------------- 期间与列表 ---------------- */
const periods = ref<string[]>([])
const period = ref('')
const query = reactive({ page: 1, size: 20 })
const total = ref(0)
const records = ref<DepreciationRecord[]>([])
const listLoading = ref(false)

async function loadPeriods() {
  try {
    const res = await fetchDepreciationPeriods()
    periods.value = res ?? []
    if (!period.value && periods.value.length) period.value = periods.value[0]
  } catch {
    // 拦截器已提示
  }
}

async function load() {
  listLoading.value = true
  try {
    const res = await fetchDepreciation({
      period: period.value || undefined,
      page: query.page,
      size: query.size,
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

/* 本页合计（表格汇总行） */
function getSummaries({ columns, data }: { columns: TableColumnCtx<DepreciationRecord>[]; data: DepreciationRecord[] }): string[] {
  return columns.map((col, index) => {
    if (index === 0) return '本页合计'
    const prop = col.property
    if (!prop || !SUM_PROPS.includes(prop)) return ''
    const sum = data.reduce((acc, row) => {
      const v = (row as unknown as Record<string, unknown>)[prop]
      return acc + (typeof v === 'number' ? v : 0)
    }, 0)
    return fmtMoney(sum)
  })
}

/* ---------------- 导出 ---------------- */
const exporting = ref(false)

async function doExport() {
  if (!period.value) {
    ElMessage.warning('请先选择期间')
    return
  }
  exporting.value = true
  try {
    const blob = await exportDepreciation(period.value)
    saveBlob(blob, `折旧底稿_${period.value}.csv`)
  } catch {
    // 拦截器已提示
  } finally {
    exporting.value = false
  }
}

/* ---------------- 执行计提 ---------------- */
const runVisible = ref(false)
const running = ref(false)
const runPeriod = ref('')

function openRun() {
  runPeriod.value = period.value || currentPeriod()
  runVisible.value = true
}

async function submitRun() {
  if (!runPeriod.value) {
    ElMessage.warning('请选择计提期间')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定执行 ${runPeriod.value} 期间折旧计提吗？将按已建档资产逐条计提。`,
      '执行计提确认',
      { type: 'warning', confirmButtonText: '执行', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  running.value = true
  try {
    const res = await runDepreciation(runPeriod.value)
    runVisible.value = false
    ElMessageBox.alert(
      `期间 ${res.period}：处理 ${res.processed} 条，跳过 ${res.skipped} 条，计提折旧合计 ${fmtMoney(res.totalAmount)} 元`,
      '计提结果',
      { type: 'success', confirmButtonText: '知道了' },
    ).catch(() => {
      // 关闭结果框
    })
    period.value = res.period || runPeriod.value
    query.page = 1
    void loadPeriods()
    void load()
  } catch {
    // 拦截器已提示
  } finally {
    running.value = false
  }
}

onMounted(async () => {
  await loadPeriods()
  if (!period.value) period.value = currentPeriod()
  void load()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">折旧底稿</div>

    <!-- 顶部筛选 -->
    <div class="page-toolbar">
      <el-select
        v-model="period"
        filterable
        allow-create
        default-first-option
        placeholder="选择已有期间"
        style="width: 150px"
        @change="search"
      >
        <el-option v-for="p in periods" :key="p" :label="p" :value="p" />
      </el-select>
      <el-date-picker
        v-model="period"
        type="month"
        value-format="YYYY-MM"
        :clearable="false"
        placeholder="选择期间"
        style="width: 150px"
        @change="search"
      />
      <el-button type="primary" plain @click="search">查询</el-button>
      <div style="flex: 1"></div>
      <el-button :loading="exporting" @click="doExport">导出 CSV</el-button>
      <el-button v-if="canWrite" type="primary" @click="openRun">执行计提</el-button>
    </div>

    <!-- 明细 -->
    <el-table
      v-loading="listLoading"
      :data="records"
      border
      stripe
      show-summary
      :summary-method="getSummaries"
      row-key="assetNo"
    >
      <el-table-column prop="assetNo" label="资产编号" min-width="140" fixed="left" />
      <el-table-column prop="assetName" label="资产名称" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.assetName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="period" label="期间" width="95">
        <template #default="{ row }">{{ row.period || '-' }}</template>
      </el-table-column>
      <el-table-column label="期初净值" min-width="110" align="right">
        <template #default="{ row }">{{ fmtMoney(row.openingNetValue) }}</template>
      </el-table-column>
      <el-table-column label="本月折旧" min-width="110" align="right">
        <template #default="{ row }">{{ fmtMoney(row.depAmount) }}</template>
      </el-table-column>
      <el-table-column label="累计折旧" min-width="110" align="right">
        <template #default="{ row }">{{ fmtMoney(row.accumulatedDep) }}</template>
      </el-table-column>
      <el-table-column label="期末净值" min-width="110" align="right">
        <template #default="{ row }">{{ fmtMoney(row.closingNetValue) }}</template>
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

    <!-- 执行计提 -->
    <el-dialog v-model="runVisible" title="执行计提" width="420px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="计提期间" required>
          <el-date-picker
            v-model="runPeriod"
            type="month"
            value-format="YYYY-MM"
            :clearable="false"
            placeholder="选择期间"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runVisible = false">取消</el-button>
        <el-button type="primary" :loading="running" @click="submitRun">执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>
