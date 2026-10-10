<script setup lang="ts">
/**
 * 扫码入库：扫码枪 / 手输物料编码 → 自动识别物料 → 累计数量 → 一次提交（整单原子）。
 * 同一物料重复扫码自动 +1；提交走批量出入库接口（任一行失败整单回滚）。
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { InputInstance } from 'element-plus'
import { createStockLogsBatch, fetchMaterialOptions, fetchWarehouseOptions } from '@/api/warehouse'
import type { MaterialOption, WarehouseOption } from '@/api/warehouse'
import { useAuthStore } from '@/stores/auth'

interface Line {
  materialCode: string
  name: string
  unit: string
  stock: number
  qty: number
}

const auth = useAuthStore()
const route = useRoute()
const canWrite = computed(() => auth.canWrite('stockLogs'))

const warehouses = ref<WarehouseOption[]>([])
const head = reactive({ warehouseCode: '', refWo: '', remark: '' })
const scan = ref('')
const scanRef = ref<InputInstance>()
const lines = ref<Line[]>([])
const busy = ref(false)
const lastHit = ref('')

const totalQty = computed(() => lines.value.reduce((s, l) => s + (l.qty || 0), 0))

function focusScan() {
  void nextTick(() => scanRef.value?.focus())
}

async function resolve(code: string): Promise<MaterialOption | null> {
  const opts = await fetchMaterialOptions({ keyword: code, size: 10 })
  const exact = opts.find((o) => o.materialCode.toUpperCase() === code.toUpperCase())
  if (exact) return exact
  if (opts.length === 1) return opts[0]
  return null
}

async function onScan(raw?: string, qty = 1) {
  const code = (raw ?? scan.value).trim()
  scan.value = ''
  if (!code) return
  const existing = lines.value.find((l) => l.materialCode.toUpperCase() === code.toUpperCase())
  if (existing) {
    existing.qty += qty
    lastHit.value = existing.materialCode
    focusScan()
    return
  }
  busy.value = true
  try {
    const m = await resolve(code)
    if (!m) {
      ElMessage.warning(`未识别的物料编码：${code}`)
      return
    }
    const hit = lines.value.find((l) => l.materialCode === m.materialCode)
    if (hit) {
      hit.qty += qty
    } else {
      lines.value.unshift({ materialCode: m.materialCode, name: m.name, unit: m.unit, stock: m.stock, qty })
    }
    lastHit.value = m.materialCode
  } catch {
    // 拦截器已提示
  } finally {
    busy.value = false
    focusScan()
  }
}

function rowClass(arg: { row: Line }): string {
  return arg.row.materialCode === lastHit.value ? 'hit' : ''
}

function removeLine(i: number) {
  lines.value.splice(i, 1)
  focusScan()
}

async function submit() {
  if (!lines.value.length) return
  if (lines.value.some((l) => !l.qty || l.qty < 1)) {
    ElMessage.warning('数量须为正整数')
    return
  }
  try {
    await ElMessageBox.confirm(`确认入库 ${lines.value.length} 种物料，共 ${totalQty.value} 件？`, '提交入库', { type: 'info' })
  } catch {
    return
  }
  busy.value = true
  try {
    await createStockLogsBatch({
      items: lines.value.map((l) => ({
        logType: 'IN' as const,
        materialCode: l.materialCode,
        qty: l.qty,
        warehouseCode: head.warehouseCode || undefined,
        refWo: head.refWo || undefined,
        remark: head.remark ? `扫码入库：${head.remark}` : '扫码入库',
      })),
    })
    ElMessage.success('入库成功')
    lines.value = []
    head.refWo = ''
    head.remark = ''
  } catch {
    // 拦截器已提示（整单回滚）
  } finally {
    busy.value = false
    focusScan()
  }
}

onMounted(async () => {
  try {
    warehouses.value = await fetchWarehouseOptions()
    head.warehouseCode = warehouses.value.find((w) => w.isDefault)?.warehouseCode || warehouses.value[0]?.warehouseCode || ''
  } catch {
    // ignore
  }
  // 从「库存水位」跳转带入建议补货量
  const m = route.query.material
  if (typeof m === 'string' && m) {
    const q = Number(route.query.qty)
    await onScan(m, Number.isInteger(q) && q > 0 ? q : 1)
  }
  focusScan()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">扫码入库</div>

    <el-alert v-if="!canWrite" type="warning" :closable="false" show-icon title="当前角色没有出入库写权限，只能预览" style="margin-bottom: 12px" />

    <el-form inline class="head">
      <el-form-item label="入库仓库">
        <el-select v-model="head.warehouseCode" style="width: 180px">
          <el-option v-for="w in warehouses" :key="w.warehouseCode" :label="w.name + (w.isDefault ? '（默认）' : '')" :value="w.warehouseCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="关联工单">
        <el-input v-model="head.refWo" placeholder="可选" style="width: 180px" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="head.remark" placeholder="如：供应商到货单号" style="width: 240px" />
      </el-form-item>
    </el-form>

    <div class="scan-box">
      <el-input
        ref="scanRef"
        v-model="scan"
        size="large"
        placeholder="将光标置于此处，用扫码枪扫描物料条码，或手动输入编码后回车"
        :disabled="busy"
        clearable
        @keyup.enter="onScan()"
      >
        <template #prefix><el-icon><Aim /></el-icon></template>
      </el-input>
    </div>

    <el-table :data="lines" border stripe :row-class-name="rowClass">
      <el-table-column type="index" width="50" />
      <el-table-column prop="materialCode" label="物料编码" width="160" />
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column label="当前库存" width="110" align="right">
        <template #default="{ row }">{{ row.stock }} {{ row.unit }}</template>
      </el-table-column>
      <el-table-column label="本次入库" width="170">
        <template #default="{ row }">
          <el-input-number v-model="row.qty" :min="1" :max="1000000" :step="1" step-strictly size="small" />
        </template>
      </el-table-column>
      <el-table-column label="" width="70">
        <template #default="{ $index }">
          <el-button link type="danger" @click="removeLine($index)">移除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :image-size="60" description="扫描第一件物料开始" />
      </template>
    </el-table>

    <div class="footer">
      <span>共 {{ lines.length }} 种，{{ totalQty }} 件</span>
      <el-button @click="lines = []">清空</el-button>
      <el-button type="primary" :disabled="!canWrite || !lines.length" :loading="busy" @click="submit">提交入库</el-button>
    </div>
  </div>
</template>

<style scoped>
.head {
  margin-bottom: 4px;
}
.scan-box {
  margin-bottom: 14px;
  max-width: 640px;
}
.footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
}
:deep(.hit) td {
  background: var(--el-color-primary-light-9) !important;
}
</style>
