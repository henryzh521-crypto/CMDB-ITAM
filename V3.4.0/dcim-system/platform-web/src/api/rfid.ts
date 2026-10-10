import http from './client'
import type { PageResult } from './types'

/** RFID 批次状态（后端 ModuleRegistry module = rfid） */
export const RFID_BATCH_STATUSES = ['草稿', '扫描中', '已确认', '已取消'] as const

/** 批次来源类型 */
export const RFID_SOURCE_TYPES = ['采购到货', 'RMA返修', '调拨'] as const

/** 资产权属 */
export const RFID_OWNER_TYPES = ['SELF', 'LEASE'] as const

/** 批次列表条目 */
export interface RfidBatchItem {
  batchNo: string
  name: string
  supplierCode?: string
  sourceType?: string
  itemCount: number
  scannedCount: number
  confirmedCount: number
  status: string
  creator?: string
  confirmedAt?: string
  remark?: string
  createdAt?: string
}

/** 批次列表查询参数 */
export interface RfidBatchQuery {
  page: number
  size: number
  status?: string
  keyword?: string
}

/** 创建批次请求体（sourceType 缺省后端默认 采购到货） */
export interface RfidBatchCreatePayload {
  name: string
  supplierCode?: string
  sourceType?: string
  remark?: string
}

/** 批次收货明细条目 */
export interface RfidBatchLine {
  id: number | string
  materialCode: string
  expectedQty: number
  receivedQty: number
}

/** 批次扫描统计 */
export interface RfidBatchStats {
  total: number
  scanned: number
  confirmed: number
  abnormal: number
}

/** 批次详情（items 为收货明细，stats 为扫描统计） */
export interface RfidBatchDetail extends RfidBatchItem {
  items: RfidBatchLine[]
  stats: RfidBatchStats | null
}

/** 批次扫描记录（scanStatus：已扫描 | 已入库 | 异常） */
export interface RfidScanRecord {
  id: number | string
  batchNo: string
  epc: string
  assetNo?: string
  materialCode?: string
  sn?: string
  scanStatus: string
  exceptionType?: string
  exceptionNote?: string
  operator?: string
  scanTime?: string
}

/** 扫描记录查询参数 */
export interface RfidScanQuery {
  page: number
  size: number
  scanStatus?: string
}

/** 明细新增请求体（重复物料后端 409） */
export interface RfidLineCreatePayload {
  materialCode: string
  expectedQty: number
}

/** 明细修改请求体（仅预期数量） */
export interface RfidLineUpdatePayload {
  expectedQty: number
}

/** 明细 CSV 导入结果 */
export interface RfidImportResult {
  total: number
  success: number
  failed: number
  errors: { row: number; reason: string }[]
}

/** 扫描请求体（epc 必填） */
export interface RfidScanPayload {
  epc: string
  sn?: string
  materialCode?: string
  exceptionNote?: string
}

/** 扫描响应：正常返回本次扫描记录字段；同 EPC 幂等返回 duplicate=true + existing（不报错） */
export interface RfidScanResult extends Partial<RfidScanRecord> {
  duplicate: boolean
  existing?: RfidScanRecord
}

/** 确认入库请求体（生成资产的默认模板） */
export interface RfidConfirmPayload {
  namePrefix?: string
  categoryCode?: string
  modelCode?: string
  ownerType?: (typeof RFID_OWNER_TYPES)[number]
  regionCode?: string
  remark?: string
}

/** 确认入库异常项 */
export interface RfidConfirmException {
  epc: string
  sn?: string
  reason: string
}

/** 确认入库结果（非扫描中后端 409） */
export interface RfidConfirmResult {
  generated: number
  skipped: number
  exceptions: RfidConfirmException[]
}

/* ---------------- 批次 ---------------- */

export function fetchRfidBatches(params: RfidBatchQuery) {
  return http.get<any, PageResult<RfidBatchItem>>('/rfid/batches', { params })
}

export function createRfidBatch(payload: RfidBatchCreatePayload) {
  return http.post<any, RfidBatchItem>('/rfid/batches', payload)
}

export function fetchRfidBatchDetail(batchNo: string) {
  return http.get<any, RfidBatchDetail>(`/rfid/batches/${encodeURIComponent(batchNo)}`)
}

export function fetchRfidBatchScans(batchNo: string, params: RfidScanQuery) {
  return http.get<any, PageResult<RfidScanRecord>>(
    `/rfid/batches/${encodeURIComponent(batchNo)}/scans`,
    { params },
  )
}

/* ---------------- 明细（仅草稿可操作） ---------------- */

export function addRfidBatchLine(batchNo: string, payload: RfidLineCreatePayload) {
  return http.post<any, void>(`/rfid/batches/${encodeURIComponent(batchNo)}/items`, payload)
}

export function updateRfidBatchLine(
  batchNo: string,
  id: number | string,
  payload: RfidLineUpdatePayload,
) {
  return http.put<any, void>(
    `/rfid/batches/${encodeURIComponent(batchNo)}/items/${encodeURIComponent(String(id))}`,
    payload,
  )
}

export function deleteRfidBatchLine(batchNo: string, id: number | string) {
  return http.delete<any, void>(
    `/rfid/batches/${encodeURIComponent(batchNo)}/items/${encodeURIComponent(String(id))}`,
  )
}

/** 明细 CSV 导入（两列：物料编码,预期数量） */
export function importRfidBatchLines(batchNo: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<FormData, RfidImportResult>(
    `/rfid/batches/${encodeURIComponent(batchNo)}/items/import`,
    form,
  )
}

/* ---------------- 流转 ---------------- */

/** 草稿 → 扫描中（无明细后端 400） */
export function startRfidBatch(batchNo: string) {
  return http.post<any, void>(`/rfid/batches/${encodeURIComponent(batchNo)}/start`)
}

/** 提交一次扫描（正常返回记录；同 EPC 幂等返回 existing） */
export function scanRfidBatch(batchNo: string, payload: RfidScanPayload) {
  return http.post<any, RfidScanResult>(`/rfid/batches/${encodeURIComponent(batchNo)}/scan`, payload)
}

/** 扫描中 → 已确认（按默认模板批量生成资产） */
export function confirmRfidBatch(batchNo: string, payload: RfidConfirmPayload) {
  return http.post<any, RfidConfirmResult>(
    `/rfid/batches/${encodeURIComponent(batchNo)}/confirm`,
    payload,
  )
}

/** 草稿/扫描中 → 已取消 */
export function cancelRfidBatch(batchNo: string) {
  return http.post<any, void>(`/rfid/batches/${encodeURIComponent(batchNo)}/cancel`)
}
