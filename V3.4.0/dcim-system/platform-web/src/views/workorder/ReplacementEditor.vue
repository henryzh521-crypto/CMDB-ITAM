<script setup lang="ts">
import { computed, onMounted, reactive, watch } from 'vue'
import { fetchAssetChildren, fetchAssets } from '@/api/asset'
import type { AssetItem } from '@/api/asset'
import { SWAP_OUT_STATUSES } from '@/api/workorder'
import type { ReplacementRow } from './replacement'
import { newReplacementRow } from './replacement'

/**
 * 子件更换明细多行编辑器（故障维修 / 部件更换）。
 *
 * 创建工单弹窗与工单详情页共用同一套交互，避免两处各写一份：
 *  - 父资产候选由调用方给（都是「本单关联资产」，与后端 `父资产须在关联资产中` 同源）
 *  - 旧件按父资产懒加载并**按父缓存**（同一台设备换多件时只拉一次）
 *  - 新件远程搜索未挂载资产
 *  - **同一设备换多件**：新增行默认继承上一行的父资产
 *  - **互斥**：已被其它行占用的旧件/新件在下拉里禁选，把 409 提前到选择时
 */
const props = defineProps<{
  /** 行模型（父组件持有，v-model 双向） */
  rows: ReplacementRow[]
  /** 父资产候选 */
  parentOptions: string[]
  /** 只读（详情页非编辑态一般直接不渲染本组件） */
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:rows', value: ReplacementRow[]): void
}>()

const rows = computed({
  get: () => props.rows,
  set: (v) => emit('update:rows', v),
})

/* ---------------- 旧件（父资产的子资产）：按父缓存 ---------------- */

const childCache = reactive(new Map<string, AssetItem[]>())
const childLoading = reactive(new Map<string, boolean>())

function childOptions(parent: string): AssetItem[] {
  return (parent && childCache.get(parent)) || []
}

function ensureChildren(parent: string) {
  if (!parent || childCache.has(parent) || childLoading.get(parent)) return
  childLoading.set(parent, true)
  fetchAssetChildren(parent, { page: 1, size: 100 })
    .then((res) => childCache.set(parent, res.records ?? []))
    .catch(() => childCache.set(parent, []))
    .finally(() => childLoading.delete(parent))
}

function onParentChange(row: ReplacementRow) {
  row.oldAssetNo = ''
  row.newAssetNo = ''
  ensureChildren(row.parentAssetNo)
}

/** 父资产候选缩水（创建页把设备从「关联资产」删了）→ 清掉失效父级，避免提交时才报错 */
function pruneInvalidParents(next: string[]) {
  for (const row of props.rows) {
    if (row.parentAssetNo && !next.includes(row.parentAssetNo)) {
      row.parentAssetNo = ''
      row.oldAssetNo = ''
      row.newAssetNo = ''
    }
  }
}

/* ---------------- 新件：远程搜索未挂载资产 ---------------- */

const newOptions = reactive(new Map<number, AssetItem[]>())
const newLoading = reactive(new Map<number, boolean>())
const newTimers = new Map<number, number>()

/** 与后端 `新件状态 ∈ 待上架/停用中/在用(计费)` 对齐（待上架在 W2 之后已无存量，仍保留） */
const NEW_ALLOWED = new Set(['待上架', '停用中', '在用(计费)'])

async function loadNewOptions(row: ReplacementRow, keyword: string) {
  newLoading.set(row.key, true)
  try {
    const res = await fetchAssets({
      page: 1,
      size: 50,
      keyword: keyword || undefined,
      parentAssetNo: 'ROOT',
    })
    newOptions.set(
      row.key,
      (res.records ?? []).filter((a) => !a.statusBiz || NEW_ALLOWED.has(a.statusBiz)),
    )
  } catch {
    newOptions.set(row.key, [])
  } finally {
    newLoading.delete(row.key)
  }
}

function onNewSearch(row: ReplacementRow, keyword: string) {
  window.clearTimeout(newTimers.get(row.key))
  newTimers.set(row.key, window.setTimeout(() => void loadNewOptions(row, keyword), 300))
}

function onNewFocus(row: ReplacementRow) {
  if (!newOptions.has(row.key)) void loadNewOptions(row, '')
}

function newOptionList(row: ReplacementRow): AssetItem[] {
  return newOptions.get(row.key) ?? []
}

/* ---------------- 互斥（已被其它行占用的旧件/新件禁选） ---------------- */

function oldTaken(assetNo: string, row: ReplacementRow): boolean {
  return props.rows.some((r) => r.key !== row.key && r.oldAssetNo === assetNo)
}

function newTaken(assetNo: string, row: ReplacementRow): boolean {
  return props.rows.some((r) => r.key !== row.key && r.newAssetNo === assetNo)
}

/* ---------------- 行操作 ---------------- */

function addRow() {
  // 同一台设备换多个部件是主场景：新行继承上一行的父资产，省掉每次重选
  const last = props.rows[props.rows.length - 1]
  const next = newReplacementRow(last?.parentAssetNo ?? '')
  if (next.parentAssetNo) ensureChildren(next.parentAssetNo)
  rows.value = [...props.rows, next]
}

function removeRow(index: number) {
  rows.value = props.rows.filter((_, i) => i !== index)
}

onMounted(() => {
  const seen = new Set<string>()
  for (const row of props.rows) {
    if (row.parentAssetNo && !seen.has(row.parentAssetNo)) {
      seen.add(row.parentAssetNo)
      ensureChildren(row.parentAssetNo)
    }
  }
})

// 只在候选**之后**缩水时清理（挂载瞬间的空候选不动手，避免误清已有行）
watch(
  () => props.parentOptions,
  (next) => pruneInvalidParents(next),
)
</script>

<template>
  <div class="rep-editor">
    <div v-for="(row, idx) in rows" :key="row.key" class="detail-row">
      <span class="detail-row-no">{{ idx + 1 }}</span>

      <el-select
        v-model="row.parentAssetNo"
        placeholder="父资产"
        style="flex: 1"
        :disabled="disabled"
        @change="onParentChange(row)"
      >
        <el-option v-for="p in parentOptions" :key="p" :label="p" :value="p" />
      </el-select>

      <el-select
        v-model="row.oldAssetNo"
        placeholder="旧件（父的子资产）"
        filterable
        style="flex: 1"
        :disabled="disabled || !row.parentAssetNo"
        :loading="!!(row.parentAssetNo && childLoading.get(row.parentAssetNo))"
      >
        <el-option
          v-for="c in childOptions(row.parentAssetNo)"
          :key="c.assetNo"
          :label="`${c.assetNo} ${c.name}（${c.statusBiz || '-'}）`"
          :value="c.assetNo"
          :disabled="oldTaken(c.assetNo, row)"
        />
      </el-select>

      <el-select
        v-model="row.newAssetNo"
        placeholder="搜索新件（未挂载）"
        filterable
        remote
        clearable
        style="flex: 1"
        :disabled="disabled"
        :loading="!!newLoading.get(row.key)"
        :remote-method="(q: string) => onNewSearch(row, q)"
        @focus="onNewFocus(row)"
      >
        <el-option
          v-for="n in newOptionList(row)"
          :key="n.assetNo"
          :label="`${n.assetNo} ${n.name}（${n.statusBiz || '-'}）`"
          :value="n.assetNo"
          :disabled="newTaken(n.assetNo, row)"
        />
      </el-select>

      <el-select
        v-model="row.oldStatus"
        placeholder="换下后状态"
        style="width: 116px"
        :disabled="disabled"
      >
        <el-option v-for="s in SWAP_OUT_STATUSES" :key="s" :label="s" :value="s" />
      </el-select>

      <el-button type="danger" link :disabled="disabled" @click="removeRow(idx)">删除</el-button>
    </div>

    <el-button v-if="!disabled" type="primary" link @click="addRow">添加换件行</el-button>

    <div class="detail-hint">
      完成工单时执行：旧件解绑并转所选「换下后状态」（默认维修中），新件挂到父资产并转「在用(计费)」；
      新件仅可选未挂载资产（待上架/停用中/在用(计费)）。同一台设备换多个部件 = 多加几行、父资产相同即可
    </div>
  </div>
</template>

<style scoped>
.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.detail-row-no {
  width: 20px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.detail-hint {
  margin-left: 28px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
