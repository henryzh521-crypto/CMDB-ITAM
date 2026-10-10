/**
 * v3.1 新增页面的 API：库存水位、巡检计划、成本分摊
 */
import http from './client'
import type { PageResult } from './types'

/* ---------------- 库存水位 ---------------- */
export type StockLevel = 'OUT' | 'LOW' | 'OVER' | 'NORMAL' | 'UNSET'

export interface StockLevelRow {
  materialCode: string
  name: string
  category?: string
  model?: string
  unit?: string
  stock: number
  safetyStock: number
  maxStock: number
  price?: number
  level: StockLevel
  out30: number
  dailyOut: number
  daysOfCover: number | null
  suggestQty: number
  warehouses: { warehouseCode: string; warehouseName?: string; qty: number }[]
}

export function fetchStockLevels(params: {
  page: number
  size: number
  keyword?: string
  level?: string
  category?: string
  warehouseCode?: string
}) {
  return http.get<any, PageResult<StockLevelRow>>('/stock-levels', { params })
}

export function fetchStockLevelSummary() {
  return http.get<any, Record<StockLevel, number> & { stockValue: number }>('/stock-levels/summary')
}

export const STOCK_LEVEL_LABEL: Record<string, string> = {
  OUT: '缺货',
  LOW: '低于安全库存',
  OVER: '超储',
  NORMAL: '正常',
  UNSET: '未设阈值',
}

/* ---------------- 巡检计划 ---------------- */
export interface InspectionPlan {
  id: number
  planNo: string
  name: string
  scopeType: 'ROOM' | 'RACK'
  scopeCodes: string[]
  frequency: 'DAILY' | 'WEEKLY' | 'MONTHLY'
  runHour: number
  runWeekday?: number
  runMonthDay?: number
  checklist: string[]
  priority: string
  assignee?: string
  enabled: boolean
  nextRunAt?: string
  lastRunAt?: string
  lastWoNo?: string
  createdBy?: string
}

export interface InspectionPlanPayload {
  name?: string
  scopeType?: string
  scopeCodes?: string[]
  frequency?: string
  runHour?: number
  runWeekday?: number
  runMonthDay?: number
  checklist?: string[]
  priority?: string
  assignee?: string
  enabled?: boolean
}

export interface InspectionRun {
  woNo: string
  runAt: string
  trigger: 'SCHEDULE' | 'MANUAL'
  status?: string
  title?: string
}

export function fetchInspectionPlans(params: { page: number; size: number; keyword?: string }) {
  return http.get<any, PageResult<InspectionPlan>>('/inspection-plans', { params })
}

export function createInspectionPlan(payload: InspectionPlanPayload) {
  return http.post<any, InspectionPlan>('/inspection-plans', payload)
}

export function updateInspectionPlan(id: number, payload: InspectionPlanPayload) {
  return http.put<any, InspectionPlan>(`/inspection-plans/${id}`, payload)
}

export function deleteInspectionPlan(id: number) {
  return http.delete<any, void>(`/inspection-plans/${id}`)
}

export function runInspectionPlan(id: number) {
  return http.post<any, { woNo: string }>(`/inspection-plans/${id}/run`)
}

export function fetchInspectionRuns(id: number) {
  return http.get<any, InspectionRun[]>(`/inspection-plans/${id}/runs`)
}

/* ---------------- 成本分摊 ---------------- */
export interface CostAllocationResult {
  period: string
  dim: string
  total: number
  rows: { key: string; assetCount: number; monthDep: number; originValue: number; netValue: number; share: number }[]
  trend: { period: string; monthDep: number; assetCount: number }[]
}

export function fetchCostAllocation(params: { period?: string; dim: string }) {
  return http.get<any, CostAllocationResult>('/finance/cost-allocation', { params })
}
