import http from './client'
import type { PageResult } from './types'

/** GPU 卡健康档位（优≥90/良≥75/观察≥60/劣化≥40/高危<40） */
export const GPU_HEALTH_LEVELS = ['优', '良', '观察', '劣化', '高危'] as const

/** GPU 卡档案条目（{整机资产号}-G{槽位}） */
export interface GpuCardItem {
  cardNo: string
  hostAssetNo: string
  slot: number
  gpuSn?: string
  model?: string
  memoryGb?: number
  fw?: string
  batchNo?: string
  migCapable?: boolean
  healthScore?: number
  healthLevel?: string
  prevSn?: string
  swapHistory?: { oldSn: string; newSn: string; at: string; refDoc?: string }[] | null
  lastScoredAt?: string
  updatedAt?: string
}

export interface GpuCardQuery {
  page: number
  size: number
  hostAssetNo?: string
  level?: string
  keyword?: string
}

/** 指标趋势点 */
export interface GpuMetricPoint {
  ts: string
  tempC?: number
  powerW?: number
  utilPct?: number
  eccDbe?: number
  xidErrors?: number
}

/** 健康度评分权重配置（四权重和=100） */
export interface GpuHealthConfig {
  weightEcc: number
  weightTemp: number
  weightXid: number
  weightPower: number
  tempLimitC: number
}

export function fetchGpuCards(params: GpuCardQuery) {
  return http.get<any, PageResult<GpuCardItem>>('/gpu/cards', { params })
}

/** 卡指标趋势（points 默认 288 ≈ 24h@5min） */
export function fetchGpuCardSeries(cardNo: string, points = 288) {
  return http.get<any, GpuMetricPoint[]>(`/gpu/cards/${encodeURIComponent(cardNo)}/series`, {
    params: { points },
  })
}

/** 手动触发全量评分，返回本轮高危自动建单数 */
export function scoreGpuCardsNow() {
  return http.post<any, number>('/gpu/score-now')
}

export function fetchGpuHealthConfig() {
  return http.get<any, GpuHealthConfig>('/gpu/health-config')
}

export function updateGpuHealthConfig(payload: GpuHealthConfig) {
  return http.put<any, boolean>('/gpu/health-config', payload)
}
