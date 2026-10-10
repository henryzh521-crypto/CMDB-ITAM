import http from './client'
import type { PageResult } from './types'

/** 财务台账条目（资产表左联 asset_finance：未建档行金额字段为 null） */
export interface FinanceAssetItem {
  assetNo: string
  assetName?: string
  categoryCode?: string
  statusBiz?: string
  statusFin?: string
  leaseFlag?: boolean
  originValue?: number | null
  residualRate?: number | null
  lifeMonths?: number | null
  depMethod?: string | null
  depStartDate?: string | null
  accumulatedDep?: number | null
  netValue?: number | null
  impairment?: number | null
}

/** 财务台账查询参数 */
export interface FinanceAssetQuery {
  page: number
  size: number
  keyword?: string
  statusFin?: string
  leaseFlag?: boolean
}

/** 财务建档请求体 */
export interface FinanceCardPayload {
  assetNo: string
  originValue: number
  residualRate?: number
  lifeMonths?: number
  depMethod?: string
  depStartDate?: string
  leaseFlag?: boolean
}

/** 减值请求体 */
export interface ImpairmentPayload {
  amount: number
}

/** 批量操作结果行：changed 带目标态，skipped 带原因（name 由前端勾选行补全） */
export interface FinanceBatchItem {
  assetNo: string
  name?: string
  to?: string
  reason?: string
}

/** 批量操作结果（部分成功汇总） */
export interface FinanceBatchResult {
  total: number
  changed: FinanceBatchItem[]
  skipped: FinanceBatchItem[]
}

/** 批量建档请求体（同批设备统一建档参数） */
export interface BatchRegisterPayload extends Omit<FinanceCardPayload, 'assetNo'> {
  assetNos: string[]
}

/** 批量减值请求体 */
export interface BatchImpairPayload {
  assetNos: string[]
  amount: number
}

/** 折旧计提执行结果 */
export interface DepreciationRunResult {
  period: string
  processed: number
  skipped: number
  totalAmount: number
}

/** 折旧底稿明细行 */
export interface DepreciationRecord {
  assetNo: string
  assetName?: string
  period: string
  openingNetValue?: number
  depAmount?: number
  accumulatedDep?: number
  closingNetValue?: number
}

/** 折旧底稿查询参数 */
export interface DepreciationQuery {
  period?: string
  page: number
  size: number
}

/** 财务台账分页查询 */
export function fetchFinanceAssets(params: FinanceAssetQuery) {
  return http.get<any, PageResult<FinanceAssetItem>>('/finance/assets', { params })
}

/** 财务建档 */
export function createFinanceCard(payload: FinanceCardPayload) {
  return http.post<any, void>('/finance/assets', payload)
}

/** 转固（开始计提折旧） */
export function capitalizeFinanceAsset(assetNo: string) {
  return http.post<any, void>(`/finance/assets/${encodeURIComponent(assetNo)}/capitalize`)
}

/** 计提减值 */
export function impairFinanceAsset(assetNo: string, payload: ImpairmentPayload) {
  return http.post<any, void>(`/finance/assets/${encodeURIComponent(assetNo)}/impairment`, payload)
}

/** 处置 */
export function disposeFinanceAsset(assetNo: string) {
  return http.post<any, void>(`/finance/assets/${encodeURIComponent(assetNo)}/dispose`)
}

/** 批量建档（部分成功） */
export function batchRegisterFinanceCards(payload: BatchRegisterPayload) {
  return http.post<any, FinanceBatchResult>('/finance/assets/batch/register', payload)
}

/** 批量转固/起租（部分成功） */
export function batchCapitalizeFinanceAssets(assetNos: string[]) {
  return http.post<any, FinanceBatchResult>('/finance/assets/batch/capitalize', { assetNos })
}

/** 批量计提减值（统一金额，部分成功） */
export function batchImpairFinanceAssets(payload: BatchImpairPayload) {
  return http.post<any, FinanceBatchResult>('/finance/assets/batch/impairment', payload)
}

/** 批量处置（部分成功） */
export function batchDisposeFinanceAssets(assetNos: string[]) {
  return http.post<any, FinanceBatchResult>('/finance/assets/batch/dispose', { assetNos })
}

/** 批量核销（部分成功） */
export function batchWriteOffFinanceAssets(assetNos: string[]) {
  return http.post<any, FinanceBatchResult>('/finance/assets/batch/write-off', { assetNos })
}

/** 核销 */
export function writeOffFinanceAsset(assetNo: string) {
  return http.post<any, void>(`/finance/assets/${encodeURIComponent(assetNo)}/write-off`)
}

/** 执行月度折旧计提 */
export function runDepreciation(period: string) {
  return http.post<any, DepreciationRunResult>('/finance/depreciation/run', { period })
}

/** 折旧底稿分页查询 */
export function fetchDepreciation(params: DepreciationQuery) {
  return http.get<any, PageResult<DepreciationRecord>>('/finance/depreciation', { params })
}

/** 折旧底稿已有期间列表 */
export function fetchDepreciationPeriods() {
  return http.get<any, string[]>('/finance/depreciation/periods')
}

/** 按期间导出折旧底稿 CSV（blob 下载） */
export async function exportDepreciation(period?: string) {
  const res = await http.get<Blob>('/finance/depreciation/export', {
    params: { period: period || undefined },
    responseType: 'blob',
  })
  return res.data
}
