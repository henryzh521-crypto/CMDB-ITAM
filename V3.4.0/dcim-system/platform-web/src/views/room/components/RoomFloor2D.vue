<script setup lang="ts">
/**
 * 机房 2D 平面图（SVG）：按 rowNo/colNo 网格布局，容量热力填色，
 * 支持滚轮缩放 / 拖拽平移 / 搜索高亮 / 点击选中机柜。
 */
import { computed, ref } from 'vue'
import type { FloorRack, RoomItem } from '@/api/room'
import type { FloorLayout } from '../floor-utils'
import { rackHeatColor, shortRackCode, type HeatMode } from '../floor-utils'

const props = defineProps<{
  layout: FloorLayout
  room?: RoomItem | null
  heatMode: HeatMode
  search?: string
  selectedRackCode?: string
}>()

const emit = defineEmits<{ select: [rack: FloorRack] }>()

/* ---------------- 几何 ---------------- */
const RACK_W = 68
const RACK_H = 96
const GAP_X = 10
const AISLE_Y = 64
const PAD = { l: 56, r: 20, t: 40, b: 28 }

const baseW = computed(
  () => PAD.l + Math.max(props.layout.maxCol, 1) * (RACK_W + GAP_X) - GAP_X + PAD.r,
)
const baseH = computed(() =>
  // 无数据时 rows=0 会让 -AISLE_Y 把高度压成负数（SVG 报 height:-20），至少留出上下内边距
  Math.max(
    PAD.t + props.layout.rowLabels.length * (RACK_H + AISLE_Y) - AISLE_Y + PAD.b,
    PAD.t + PAD.b,
  ),
)

function cellX(col: number): number {
  return PAD.l + col * (RACK_W + GAP_X)
}
function cellY(row: number): number {
  return PAD.t + row * (RACK_H + AISLE_Y)
}

/* ---------------- 缩放 / 平移 ---------------- */
const zoom = ref(1)
const panX = ref(0)
const panY = ref(0)

const viewBox = computed(() => {
  const w = baseW.value / zoom.value
  const h = baseH.value / zoom.value
  const x = (baseW.value - w) / 2 + panX.value
  const y = (baseH.value - h) / 2 + panY.value
  return `${x} ${y} ${w} ${h}`
})

function zoomBy(delta: number, anchor?: { x: number; y: number }) {
  const prev = zoom.value
  const next = Math.max(0.4, Math.min(4, prev * delta))
  if (next === prev) return
  if (anchor) {
    // 以锚点为中心缩放：调整 pan 使锚点屏幕位置近似不变
    const k = 1 - prev / next
    panX.value += (anchor.x - baseW.value / 2) * k
    panY.value += (anchor.y - baseH.value / 2) * k
  }
  zoom.value = next
}

function resetView() {
  zoom.value = 1
  panX.value = 0
  panY.value = 0
}

function onWheel(e: WheelEvent) {
  e.preventDefault()
  zoomBy(e.deltaY < 0 ? 1.12 : 1 / 1.12)
}

/* 拖拽平移 */
const dragging = ref(false)
let dragStart = { x: 0, y: 0, panX: 0, panY: 0 }

function onPointerDown(e: PointerEvent) {
  if (e.button !== 0) return
  dragging.value = true
  dragStart = { x: e.clientX, y: e.clientY, panX: panX.value, panY: panY.value }
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value) return
  const el = e.currentTarget as HTMLElement
  const dx = e.clientX - dragStart.x
  const dy = e.clientY - dragStart.y
  // viewBox 单位 / 客户端像素 的近似换算（meet 等比缩放下足够跟手）
  const sx = baseW.value / zoom.value / (el.clientWidth || 1)
  const sy = baseH.value / zoom.value / (el.clientHeight || 1)
  panX.value = dragStart.panX - dx * sx
  panY.value = dragStart.panY - dy * sy
}

function onPointerUp(e: PointerEvent) {
  dragging.value = false
  try {
    ;(e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId)
  } catch {
    /* noop */
  }
}

/* ---------------- 交互状态 ---------------- */
const hovered = ref<string>('')

function dimmed(rack: FloorRack): boolean {
  const kw = (props.search || '').trim().toLowerCase()
  if (!kw) return false
  return !rack.rackCode.toLowerCase().includes(kw) && !(rack.locationName || '').toLowerCase().includes(kw)
}

function onSelect(rack: FloorRack) {
  emit('select', rack)
}

const rowLabelText = (label: string) => (label === '' ? '—' : `${label}排`)
</script>

<template>
  <div class="floor2d">
    <div class="floor2d-zoom">
      <el-button size="small" @click="zoomBy(1.25)">＋</el-button>
      <el-button size="small" @click="zoomBy(1 / 1.25)">－</el-button>
      <el-button size="small" @click="resetView">复位</el-button>
    </div>
    <svg
      class="floor-svg"
      :viewBox="viewBox"
      preserveAspectRatio="xMidYMid meet"
      @wheel="onWheel"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointerleave="dragging = false"
    >
      <!-- 机房外轮廓 -->
      <rect
        :x="PAD.l - 30"
        :y="PAD.t - 26"
        :width="baseW - PAD.l - PAD.r + 50"
        :height="baseH - PAD.t - PAD.b + 44"
        fill="#f8fafc"
        stroke="#cbd5e1"
        stroke-width="2"
        rx="6"
      />
      <text :x="PAD.l - 18" :y="PAD.t - 10" class="room-tag">
        {{ room?.roomCode }} {{ room?.name }}
        <tspan v-if="room?.areaSqm" class="room-area"> · {{ room.areaSqm }}㎡</tspan>
      </text>

      <!-- 列号表头 -->
      <text
        v-for="c in layout.maxCol"
        :key="'c' + c"
        :x="cellX(c - 1) + RACK_W / 2"
        :y="PAD.t - 12"
        text-anchor="middle"
        class="col-head"
      >
        {{ c }}
      </text>

      <!-- 行标签 + 通道示意 -->
      <g v-for="(label, ri) in layout.rowLabels" :key="'r' + ri">
        <text :x="PAD.l - 36" :y="cellY(ri) + RACK_H / 2" text-anchor="middle" class="row-head">
          {{ rowLabelText(label) }}
        </text>
        <text
          v-if="ri < layout.rowLabels.length - 1"
          :x="PAD.l + (layout.maxCol * (RACK_W + GAP_X)) / 2"
          :y="cellY(ri) + RACK_H + AISLE_Y / 2 + 3"
          text-anchor="middle"
          class="aisle-tag"
        >
          通道
        </text>
      </g>

      <!-- 机柜 -->
      <g
        v-for="cell in layout.cells"
        :key="cell.rack.rackCode"
        class="rack-cell"
        :class="{ selected: cell.rack.rackCode === selectedRackCode, dimmed: dimmed(cell.rack) }"
        @click="onSelect(cell.rack)"
        @mouseenter="hovered = cell.rack.rackCode"
        @mouseleave="hovered === cell.rack.rackCode && (hovered = '')"
      >
        <title>
          {{ cell.rack.rackCode }} · 已用 {{ cell.rack.usedUnits }}/{{ cell.rack.uCapacity }}U
          （{{ Math.round((cell.rack.usageRate || 0) * 100) }}%）
          {{ cell.rack.locationName ? ' · ' + cell.rack.locationName : '' }}
        </title>
        <rect
          :x="cellX(cell.gridCol)"
          :y="cellY(cell.gridRow)"
          :width="RACK_W"
          :height="RACK_H"
          :fill="rackHeatColor(cell.rack, heatMode)"
          :fill-opacity="hovered === cell.rack.rackCode ? 1 : 0.88"
          :stroke="cell.rack.rackCode === selectedRackCode ? '#2563eb' : '#94a3b8'"
          :stroke-width="cell.rack.rackCode === selectedRackCode ? 2.5 : 1"
          rx="4"
        />
        <!-- 朝向指示（正面朝下） -->
        <polygon
          :points="`${cellX(cell.gridCol) + RACK_W / 2 - 5},${cellY(cell.gridRow) + RACK_H - 7} ${cellX(cell.gridCol) + RACK_W / 2 + 5},${cellY(cell.gridRow) + RACK_H - 7} ${cellX(cell.gridCol) + RACK_W / 2},${cellY(cell.gridRow) + RACK_H - 1}`"
          fill="#1f2937"
          fill-opacity="0.55"
        />
        <text
          :x="cellX(cell.gridCol) + RACK_W / 2"
          :y="cellY(cell.gridRow) + RACK_H / 2 - 6"
          text-anchor="middle"
          class="rack-code"
        >
          {{ shortRackCode(cell.rack.rackCode) }}
        </text>
        <text
          :x="cellX(cell.gridCol) + RACK_W / 2"
          :y="cellY(cell.gridRow) + RACK_H / 2 + 12"
          text-anchor="middle"
          class="rack-u"
        >
          {{ cell.rack.usedUnits }}/{{ cell.rack.uCapacity }}U
        </text>
      </g>
    </svg>
  </div>
</template>

<style scoped>
.floor2d {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 420px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: hidden;
}

.floor-svg {
  display: block;
  width: 100%;
  height: 100%;
  cursor: grab;
  user-select: none;
  touch-action: none;
}

.floor-svg:active {
  cursor: grabbing;
}

.floor2d-zoom {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  display: flex;
  gap: 6px;
}

.room-tag {
  font-size: 13px;
  font-weight: 600;
  fill: #374151;
}

.room-area {
  font-weight: 400;
  fill: #9ca3af;
}

.col-head,
.row-head {
  font-size: 11px;
  fill: #6b7280;
}

.aisle-tag {
  font-size: 10px;
  fill: #c0c4cc;
}

.rack-cell {
  cursor: pointer;
}

.rack-cell.dimmed {
  opacity: 0.18;
}

.rack-code {
  font-size: 12px;
  font-weight: 700;
  fill: #111827;
  pointer-events: none;
}

.rack-u {
  font-size: 10px;
  fill: #1f2937;
  fill-opacity: 0.75;
  pointer-events: none;
}
</style>
