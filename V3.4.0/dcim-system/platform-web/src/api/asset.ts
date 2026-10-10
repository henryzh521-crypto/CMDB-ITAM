import http from './client'
import type { PageResult } from './types'

/** 资产台账条目 */
export interface AssetItem {
  assetNo: string
  sn?: string
  name: string
  categoryCode?: string
  modelCode?: string
  ownerType?: string
  statusBiz?: string
  statusFin?: string
  regionCode?: string
  roomCode?: string
  rackCode?: string
  unitNo?: number
  uHeight?: number
  department?: string
  owner?: string
  mgmtIp?: string
  bizIp?: string
  purchaseDate?: string
  purchasePrice?: number
  warrantyEnd?: string
  rackDate?: string
  contractNo?: string
  /** 额定/预估功率（W），为空时按型号/分类功率模板估算 */
  ratedPowerW?: number | null
  /** 父资产编号（整机-部件；空=根资产） */
  parentAssetNo?: string | null
  attrs?: Record<string, unknown> | null
  version?: number
  createdAt?: string
  updatedAt?: string
}

/** 资产财务信息（详情中可能为 null） */
export interface AssetFinance {
  originValue?: number
  residualRate?: number
  lifeMonths?: number
  depMethod?: string
  depStartDate?: string
  accumulatedDep?: number
  netValue?: number
  impairment?: number
  leaseFlag?: boolean
}

export interface AssetDetail extends AssetItem {
  finance?: AssetFinance | null
  /** 父资产摘要（无父则为 null） */
  parent?: { assetNo: string; name: string; statusBiz?: string; categoryCode?: string } | null
  /** 直接子资产（最多 200） */
  children?: AssetItem[]
  /** 子资产总数 */
  childCount?: number
}

/** 资产列表查询参数 */
export interface AssetQuery {
  page: number
  size: number
  keyword?: string
  statusBiz?: string
  categoryCode?: string
  regionCode?: string
  roomCode?: string
  /** 父资产编号；ROOT=仅根；CHILD=仅子资产 */
  parentAssetNo?: string
}

/** 新增 / 编辑资产请求体 */
export interface AssetSavePayload {
  name: string
  sn?: string
  categoryCode?: string
  modelCode?: string
  ownerType?: string
  regionCode?: string
  roomCode?: string
  rackCode?: string
  unitNo?: number
  uHeight?: number
  department?: string
  owner?: string
  mgmtIp?: string
  bizIp?: string
  purchaseDate?: string
  purchasePrice?: number
  warrantyEnd?: string
  rackDate?: string
  contractNo?: string
  /** 额定/预估功率（W），为空时按型号/分类功率模板估算 */
  ratedPowerW?: number | null
  /** 父资产编号；空串/undefined 表示不清（编辑时传 "" 解除挂接） */
  parentAssetNo?: string
  attrs?: Record<string, unknown>
}

/** 状态流转请求体 */
export interface AssetTransitionPayload {
  targetBiz?: string
  targetFin?: string
  reason?: string
  refDoc?: string
  /** 状态联动到全部子孙 */
  cascadeChildren?: boolean
}

/** 级联流转汇总（响应中） */
export interface AssetCascadeResult {
  cascaded: { assetNo: string; name?: string; from?: string; to?: string }[]
  skipped: { assetNo: string; name?: string; from?: string; reason?: string }[]
  target: string
}

/** 资产生命周期事件 */
export interface AssetEvent {
  id: number | string
  assetNo: string
  eventType: string
  operator?: string
  refDoc?: string
  snapshot?: string
  eventTime?: string
}

/** 资产全生命周期时间线项（v3.6：聚合台账/工单/财务/告警四源） */
export interface AssetTimelineItem {
  id: number | string
  source: 'asset' | 'workorder' | 'fin' | 'alert'
  eventType: string
  operator?: string
  refDoc?: string
  /** JSON 字符串（后端 ::text 惯例），展示前需 parse */
  detail?: string
  eventTime?: string
}

/** 资产元数据（状态下拉、区域等） */
export interface AssetMeta {
  bizStates: string[]
  finStates: string[]
  regions: { key: string; label: string }[]
  ownerTypes: string[]
}

/** 批量导入结果 */
export interface AssetImportResult {
  total: number
  success: number
  failed: number
  errors: { row: number; reason: string }[]
}

/** 批量操作单台结果 */
export interface AssetBatchItem {
  assetNo: string
  name?: string
  from?: string
  to?: string
  reason?: string
}

/** 批量流转 / 编辑 / 删除 结果（部分成功） */
export interface AssetBatchResult {
  total: number
  changed: AssetBatchItem[]
  skipped: AssetBatchItem[]
  target?: string
}

/** 批量状态流转请求（任意目标，非法逐台 skip） */
export interface AssetBatchTransitionPayload {
  assetNos: string[]
  targetBiz?: string
  targetFin?: string
  reason?: string
  refDoc?: string
}

/** 批量编辑请求：出现的字段才更新，空白=清空；不含状态/SN */
export interface AssetBatchUpdatePayload {
  assetNos: string[]
  categoryCode?: string
  modelCode?: string
  regionCode?: string
  ownerType?: string
  department?: string
  owner?: string
  contractNo?: string
  /** 额定/预估功率（W），为空时按型号/分类功率模板估算 */
  ratedPowerW?: number | null
}

export function fetchAssets(params: AssetQuery) {
  return http.get<any, PageResult<AssetItem>>('/assets', { params })
}

export function fetchAssetDetail(assetNo: string) {
  return http.get<any, AssetDetail>(`/assets/${encodeURIComponent(assetNo)}`)
}

export function createAsset(payload: AssetSavePayload) {
  return http.post<any, { assetNo: string }>('/assets', payload)
}

export function updateAsset(assetNo: string, payload: AssetSavePayload & { version: number }) {
  return http.put<any, void>(`/assets/${encodeURIComponent(assetNo)}`, payload)
}

export function deleteAsset(assetNo: string) {
  return http.delete<any, void>(`/assets/${encodeURIComponent(assetNo)}`)
}

export function transitionAsset(assetNo: string, payload: AssetTransitionPayload) {
  return http.post<any, void>(`/assets/${encodeURIComponent(assetNo)}/transitions`, payload)
}

/** 批量状态流转（部分成功） */
export function batchTransitionAssets(payload: AssetBatchTransitionPayload) {
  return http.post<any, AssetBatchResult>('/assets/batch-transitions', payload)
}

/** 批量编辑（仅更新出现的字段，部分成功） */
export function batchUpdateAssets(payload: AssetBatchUpdatePayload) {
  return http.post<any, AssetBatchResult>('/assets/batch-update', payload)
}

/** 批量删除（仅管理员，部分成功） */
export function batchDeleteAssets(assetNos: string[]) {
  return http.post<any, AssetBatchResult>('/assets/batch-delete', { assetNos })
}

export function fetchAssetEvents(assetNo: string, params: { page: number; size: number }) {
  return http.get<any, PageResult<AssetEvent>>(`/assets/${encodeURIComponent(assetNo)}/events`, {
    params,
  })
}

/** v3.6：资产全生命周期时间线（台账事件 + 工单 + 折旧计提 + 告警） */
export function fetchAssetTimeline(assetNo: string, params: { page: number; size: number; source?: string }) {
  return http.get<any, PageResult<AssetTimelineItem>>(`/assets/${encodeURIComponent(assetNo)}/timeline`, {
    params,
  })
}

export function fetchAssetMeta() {
  return http.get<any, AssetMeta>('/assets/meta')
}

/** 型号建议值（型号库已下线，model_code 为资产自由文本；返回库内已用过的值） */
export function fetchAssetModelCodes(params: { keyword?: string; categoryCode?: string } = {}) {
  return http.get<any, string[]>('/assets/model-codes', { params })
}

/** 直接子资产分页 */
export function fetchAssetChildren(assetNo: string, params: { page?: number; size?: number } = {}) {
  return http.get<any, PageResult<AssetItem>>(
    `/assets/${encodeURIComponent(assetNo)}/children`,
    { params: { page: 1, size: 50, ...params } },
  )
}

/** 树节点（children 递归） */
export interface AssetTreeItem extends AssetItem {
  children?: AssetTreeItem[]
}

/** 整机树（rootAssetNo 可选，指定则以该资产为根） */
export function fetchAssetTree(rootAssetNo?: string) {
  return http.get<any, AssetTreeItem[]>('/assets/tree', {
    params: rootAssetNo ? { rootAssetNo } : {},
  })
}

/** 按当前筛选或勾选行导出 CSV（assetNos 非空时只导出选中） */
export async function exportAssets(
  params: Omit<AssetQuery, 'page' | 'size'> & { assetNos?: string },
) {
  const res = await http.get<Blob>('/assets/export', {
    params,
    responseType: 'blob',
  })
  return res.data
}

/** 下载导入模板 CSV */
export async function fetchImportTemplate() {
  const res = await http.get<Blob>('/assets/template', { responseType: 'blob' })
  return res.data
}

/** 批量导入 CSV */
export function importAssets(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<FormData, AssetImportResult>('/assets/import', form)
}

/** 触发浏览器保存 blob 文件 */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
