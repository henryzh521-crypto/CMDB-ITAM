<script setup lang="ts">
/**
 * 机柜 2D 立面图（SVG）：U 标尺 + 按 occupiedBy 合并的设备块
 * 状态色沿用机柜管理 U 位平面图语义；compact 用于抽屉内嵌。
 */
import { computed } from 'vue'
import type { RackUnit } from '@/api/room'
import { mergeUnitBlocks, unitStatusColor } from '../floor-utils'

const props = withDefaults(
  defineProps<{
    units: RackUnit[]
    uCapacity: number
    rackCode?: string
    compact?: boolean
  }>(),
  { rackCode: '', compact: false },
)

const emit = defineEmits<{ select: [block: ReturnType<typeof mergeUnitBlocks>[number]] }>()

const uPx = computed(() => (props.compact ? 11 : 16))
const rackW = computed(() => (props.compact ? 150 : 220))
const rulerW = 44
const padT = 22
const padB = 10

const blocks = computed(() => mergeUnitBlocks(props.units, props.uCapacity))

const svgW = computed(() => rulerW + rackW.value + 2)
const svgH = computed(() => padT + props.uCapacity * uPx.value + padB)

/** U n 的顶边 y（U capacity 在最上，U1 在最下） */
function yTop(unitNo: number): number {
  return padT + (props.uCapacity - unitNo) * uPx.value
}

function blockY(fromU: number, toU: number): number {
  return yTop(toU)
}

function blockH(height: number): number {
  return height * uPx.value
}

/** 每 5U 加粗刻度 */
function isMajor(unitNo: number): boolean {
  return unitNo % 5 === 0
}

const rulerTicks = computed(() => {
  const arr: number[] = []
  for (let n = props.uCapacity; n >= 1; n--) arr.push(n)
  return arr
})
</script>

<template>
  <div class="elev2d">
    <div class="elev2d-legend">
      <span class="lg"><i style="background: #67c23a"></i>空闲</span>
      <span class="lg"><i style="background: #409eff"></i>占用</span>
      <span class="lg"><i style="background: #e6a23c"></i>预留</span>
      <span class="lg"><i style="background: #f56c6c"></i>故障</span>
      <span v-if="rackCode" class="lg-code">{{ rackCode }} · {{ uCapacity }}U</span>
    </div>
    <div class="elev2d-scroll">
      <svg :width="svgW" :height="svgH" :viewBox="`0 0 ${svgW} ${svgH}`" class="elev-svg">
        <!-- 机柜外框 -->
        <rect
          :x="rulerW"
          :y="padT - 4"
          :width="rackW"
          :height="uCapacity * uPx + 8"
          fill="#fafafa"
          stroke="#c0c4cc"
          :rx="3"
        />
        <!-- U 标尺 -->
        <g class="ruler">
          <text
            v-for="n in rulerTicks"
            :key="n"
            :x="rulerW - 6"
            :y="yTop(n) + uPx / 2"
            text-anchor="end"
            :dominant-baseline="isMajor(n) ? 'middle' : 'middle'"
            :class="['tick', { major: isMajor(n) }]"
          >
            U{{ n }}
          </text>
          <line
            v-for="n in rulerTicks"
            :key="'t' + n"
            :x1="rulerW - 2"
            :x2="rulerW"
            :y1="yTop(n)"
            :y2="yTop(n)"
            :stroke="isMajor(n) ? '#909399' : '#dcdfe6'"
          />
        </g>
        <!-- 每 U 细分隔线 -->
        <line
          v-for="n in rulerTicks.slice(1)"
          :key="'s' + n"
          :x1="rulerW"
          :x2="rulerW + rackW"
          :y1="yTop(n)"
          :y2="yTop(n)"
          stroke="#f0f2f5"
          stroke-width="1"
        />
        <!-- 设备/状态块 -->
        <g
          v-for="(b, i) in blocks"
          :key="i"
          class="blk"
          :class="{ clickable: b.status !== '空闲' }"
          @click="b.status !== '空闲' && emit('select', b)"
        >
          <title>
            {{ b.status === '占用'
              ? `U${b.fromU}-U${b.toU} ${b.assetName || b.occupiedBy || ''}（${b.height}U）${b.assetStatusBiz ? ' · ' + b.assetStatusBiz : ''}`
              : `U${b.fromU}-U${b.toU} ${b.status}` }}
          </title>
          <rect
            :x="rulerW + 2"
            :y="blockY(b.fromU, b.toU)"
            :width="rackW - 4"
            :height="blockH(b.height) - 1"
            :fill="b.status === '空闲' ? 'transparent' : unitStatusColor(b.status)"
            :fill-opacity="b.status === '空闲' ? 0 : 0.9"
            :stroke="b.status === '空闲' ? 'none' : '#ffffff'"
            stroke-width="0.5"
            :rx="2"
          />
          <text
            v-if="b.status !== '空闲' && blockH(b.height) >= 12"
            :x="rulerW + rackW / 2"
            :y="blockY(b.fromU, b.toU) + blockH(b.height) / 2"
            text-anchor="middle"
            dominant-baseline="middle"
            class="blk-label"
          >
            {{ b.status === '占用' ? (b.assetName || b.occupiedBy || '-') : b.status }}
            <tspan v-if="b.height > 1" class="blk-h"> · {{ b.height }}U</tspan>
          </text>
        </g>
        <!-- 底部 U1 基线 -->
        <line
          :x1="rulerW"
          :x2="rulerW + rackW"
          :y1="padT + uCapacity * uPx"
          :y2="padT + uCapacity * uPx"
          stroke="#c0c4cc"
        />
      </svg>
    </div>
  </div>
</template>

<style scoped>
.elev2d {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.elev2d-legend {
  display: flex;
  gap: 14px;
  align-items: center;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.lg {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.lg i {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

.lg-code {
  margin-left: auto;
  font-family: Consolas, Monaco, monospace;
  color: var(--el-text-color-secondary);
}

.elev2d-scroll {
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
  max-height: 72vh;
}

.elev-svg {
  display: block;
}

.tick {
  font-size: 9px;
  fill: #909399;
  font-family: Consolas, Monaco, monospace;
}

.tick.major {
  font-size: 10px;
  fill: #606266;
  font-weight: 600;
}

.blk.clickable {
  cursor: pointer;
}

.blk.clickable:hover rect {
  filter: brightness(1.08);
}

.blk-label {
  font-size: 10px;
  fill: #fff;
  pointer-events: none;
}

.blk-h {
  font-size: 9px;
  opacity: 0.85;
}
</style>
