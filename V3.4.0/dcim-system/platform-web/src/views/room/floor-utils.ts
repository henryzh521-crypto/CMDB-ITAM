import type { FloorRack, RackUnit } from '@/api/room'

/** U 位状态色（与机柜管理 U 位平面图一致） */
export function unitStatusColor(status?: string): string {
  const s = status || '空闲'
  if (s === '占用') return '#409eff'
  if (s === '预留') return '#e6a23c'
  if (s === '故障') return '#f56c6c'
  return '#67c23a'
}

/** 热力维度：usage=U使用率(越高越红) / free=空闲U(越多越绿) */
export type HeatMode = 'usage' | 'free'

function hexToRgb(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

function rgbToHex(r: number, g: number, b: number): string {
  const c = (v: number) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')
  return `#${c(r)}${c(g)}${c(b)}`
}

function lerpHex(a: string, b: string, t: number): string {
  const ra = hexToRgb(a)
  const rb = hexToRgb(b)
  return rgbToHex(ra[0] + (rb[0] - ra[0]) * t, ra[1] + (rb[1] - ra[1]) * t, ra[2] + (rb[2] - ra[2]) * t)
}

/**
 * 容量热力色：红黄绿（业界 DCIM 容量编码惯例）
 * @param ratio 0~1 的"越满越红"比例
 */
export function heatColor(ratio: number): string {
  const t = Math.max(0, Math.min(1, ratio))
  if (t <= 0.5) return lerpHex('#67c23a', '#e6a23c', t / 0.5)
  return lerpHex('#e6a23c', '#f56c6c', (t - 0.5) / 0.5)
}

/** 机柜热力填色：按热力维度归一到 0~1（越满越红） */
export function rackHeatColor(rack: FloorRack, mode: HeatMode): string {
  const cap = rack.uCapacity || 0
  if (mode === 'free') {
    return heatColor(cap === 0 ? 0 : 1 - rack.usedUnits / cap)
  }
  return heatColor(rack.usageRate ?? (cap === 0 ? 0 : rack.usedUnits / cap))
}

/** 平面布局结果：单柜屏幕坐标（2D 与 3D 共用同一套行列推导） */
export interface FloorCell {
  rack: FloorRack
  /** 网格行序（0 起，自上而下） */
  gridRow: number
  /** 网格列序（0 起，自左向右） */
  gridCol: number
}

export interface FloorLayout {
  cells: FloorCell[]
  /** 行标签（按 gridRow 序） */
  rowLabels: string[]
  /** 全局最大列数 */
  maxCol: number
}

/** 按 effRow/effCol 生成网格布局：行按标签自然序，列按 effCol（同排连续化） */
export function buildFloorLayout(racks: FloorRack[]): FloorLayout {
  const rowKey = (r: FloorRack) => String(r.effRow ?? r.rowNo ?? '')
  // 行标签自然序（数字优先）
  const labelSet = [...new Set(racks.map(rowKey))]
  labelSet.sort((a, b) => {
    const na = Number(a)
    const nb = Number(b)
    if (!Number.isNaN(na) && !Number.isNaN(nb) && a !== '' && b !== '') return na - nb
    return a.localeCompare(b, 'zh-CN')
  })
  const rowIndex = new Map(labelSet.map((l, i) => [l, i]))

  const cells: FloorCell[] = []
  const colSeen: Map<number, number>[] = labelSet.map(() => new Map())
  for (const rack of racks) {
    const gr = rowIndex.get(rowKey(rack)) ?? 0
    const wanted = rack.effCol ?? rack.colNo ?? 0
    const used = colSeen[gr]
    let gc = wanted
    while (used.has(gc)) gc += 1
    used.set(gc, 1)
    cells.push({ rack, gridRow: gr, gridCol: Math.max(0, gc - 1) })
  }
  // 列序紧凑：同排内按 gridCol 排序后重映射为 0..n-1，避免大 gap 拉爆画布
  const byRow = new Map<number, FloorCell[]>()
  for (const c of cells) {
    const arr = byRow.get(c.gridRow) || []
    arr.push(c)
    byRow.set(c.gridRow, arr)
  }
  let maxCol = 0
  for (const arr of byRow.values()) {
    arr.sort((a, b) => a.gridCol - b.gridCol || a.rack.rackCode.localeCompare(b.rack.rackCode))
    arr.forEach((c, i) => {
      c.gridCol = i
    })
    maxCol = Math.max(maxCol, arr.length)
  }
  // 行整体也紧凑（无机柜的空行标签仍保留展示用，但 gridRow 已由 labelSet 决定）
  return { cells, rowLabels: labelSet, maxCol }
}

/** U 位区间合并：连续且同 status+occupiedBy 的 U 合成一块（设备/预留段） */
export interface UnitBlock {
  /** 段底 U（较小值） */
  fromU: number
  /** 段顶 U（较大值） */
  toU: number
  height: number
  status: string
  occupiedBy?: string
  assetName?: string
  assetStatusBiz?: string
}

export function mergeUnitBlocks(units: RackUnit[], uCapacity: number): UnitBlock[] {
  const byNo = new Map<number, RackUnit>()
  for (const u of units) byNo.set(u.unitNo, u)
  const blocks: UnitBlock[] = []
  let n = 1
  while (n <= uCapacity) {
    const u = byNo.get(n)
    const status = u?.status || '空闲'
    const key = status === '占用' ? u?.occupiedBy || '' : ''
    let top = n
    while (top + 1 <= uCapacity) {
      const nx = byNo.get(top + 1)
      const ns = nx?.status || '空闲'
      const nk = ns === '占用' ? nx?.occupiedBy || '' : ''
      if (ns === status && nk === key) {
        top += 1
      } else {
        break
      }
    }
    blocks.push({
      fromU: n,
      toU: top,
      height: top - n + 1,
      status,
      occupiedBy: u?.occupiedBy,
      assetName: u?.assetName,
      assetStatusBiz: u?.assetStatusBiz,
    })
    n = top + 1
  }
  return blocks
}

/** 机柜短名：去掉机房前缀（QY01-R03 → R03），超长时截断 */
export function shortRackCode(rackCode: string): string {
  const i = rackCode.lastIndexOf('-')
  const tail = i >= 0 ? rackCode.slice(i + 1) : rackCode
  return tail.length > 10 ? rackCode : tail
}
