import http from './client'
import type { PageResult } from './types'

/* ---------------- 自动发现（PRD F02） ---------------- */

/** 发现任务状态 */
export const DISCOVER_TASK_STATUSES = ['待执行', '扫描中', '已完成', '失败'] as const

/** 发现协议 */
export const DISCOVER_PROTOCOLS = ['REDFISH', 'IPMI'] as const

/** 发现结果状态（待认领/已匹配/已认领/已忽略） */
export const DISCOVER_RESULT_STATUSES = ['待认领', '已匹配', '已认领', '已忽略'] as const

/** 发现任务条目 */
export interface DiscoverTaskItem {
  id: number
  taskNo: string
  name: string
  cidr: string
  protocol: string
  credentialId?: number
  regionCode?: string
  status: string
  totalIps?: number
  scannedIps: number
  foundCount: number
  matchedCount: number
  pendingCount: number
  lastRunAt?: string
  createdBy?: string
  createdAt?: string
}

export interface DiscoverTaskQuery {
  page: number
  size: number
  status?: string
  keyword?: string
}

export interface DiscoverTaskCreatePayload {
  name: string
  cidr: string
  protocol: string
  credentialId?: number
  regionCode?: string
}

/** IRE 裁决（可回放） */
export interface DiscoverDecision {
  matchType?: string
  action?: string
  reason?: string
  conflicts?: { field: string; existing?: unknown; incoming?: unknown }[]
  [key: string]: unknown
}

/** 发现结果条目（待认领池） */
export interface DiscoverResultItem {
  id: number
  taskId: number
  bmcIp: string
  sn: string
  manufacturer?: string
  model?: string
  endpointId?: number
  assetNo?: string
  status: string
  decision?: DiscoverDecision | null
  claimedAt?: string
  createdAt?: string
}

export interface DiscoverResultQuery {
  page: number
  size: number
  taskId?: number
  status?: string
  keyword?: string
}

/** 认领请求（SN 已在台账→确认匹配；否则建档 name 必填） */
export interface DiscoverClaimPayload {
  name?: string
  categoryCode?: string
  modelCode?: string
  regionCode?: string
}

/** 带外凭据（任何接口不回显密码） */
export interface BmcCredentialItem {
  id: number
  credName: string
  username: string
  createdAt?: string
}

export interface BmcCredentialCreatePayload {
  credName: string
  username: string
  password: string
}

/* ---------------- 带外档案（PRD F06） ---------------- */

/** BMC 端点条目 */
export interface BmcEndpointItem {
  id: number
  bmcIp: string
  mac?: string
  assetNo?: string
  assetName?: string
  credentialId?: number
  credentialName?: string
  redfishVersion?: string
  ipmiVersion?: string
  collectIntervalMin: number
  consecutiveFailures: number
  lastCollectAt?: string
  lastCollectOk?: boolean
  nextCollectAt?: string
  disabled: boolean
  createdAt?: string
}

export interface BmcEndpointQuery {
  page: number
  size: number
  keyword?: string
  disabled?: boolean
  unmanaged?: boolean
}

/** 端点建/改请求体（bmcIp 仅创建时必填） */
export interface BmcEndpointUpsertPayload {
  bmcIp?: string
  mac?: string
  assetNo?: string
  credentialId?: number
  redfishVersion?: string
  ipmiVersion?: string
  collectIntervalMin?: number
  disabled?: boolean
}

/** 导入行（bmcIp,assetNo） */
export interface EndpointImportRow {
  bmcIp: string
  assetNo?: string
}

/** 导入结果（部分成功语义） */
export interface EndpointImportResult {
  created: number
  updated: number
  errors: string[]
}

/** 配置快照条目 */
export interface HwSnapshotItem {
  id: number
  endpointId: number
  assetNo?: string
  protocol?: string
  snapshot?: Record<string, unknown> | null
  specDiff?: Record<string, { expected?: unknown; actual?: unknown }> | null
  ok: boolean
  error?: string
  collectedAt?: string
}

/* ---------------- 发现任务 ---------------- */

export function fetchDiscoverTasks(params: DiscoverTaskQuery) {
  return http.get<any, PageResult<DiscoverTaskItem>>('/discovery/tasks', { params })
}

export function createDiscoverTask(payload: DiscoverTaskCreatePayload) {
  return http.post<any, DiscoverTaskItem>('/discovery/tasks', payload)
}

/** 立即执行：重置为待执行让 agent 领取 */
export function runDiscoverTask(taskNo: string) {
  return http.post<any, boolean>(`/discovery/tasks/${encodeURIComponent(taskNo)}/run`)
}

export function deleteDiscoverTask(taskNo: string) {
  return http.delete<any, boolean>(`/discovery/tasks/${encodeURIComponent(taskNo)}`)
}

/* ---------------- 待认领池 ---------------- */

export function fetchDiscoverResults(params: DiscoverResultQuery) {
  return http.get<any, PageResult<DiscoverResultItem>>('/discovery/results', { params })
}

export function claimDiscoverResult(id: number, payload: DiscoverClaimPayload) {
  return http.post<any, DiscoverResultItem>(`/discovery/results/${id}/claim`, payload)
}

export function ignoreDiscoverResult(id: number) {
  return http.post<any, boolean>(`/discovery/results/${id}/ignore`)
}

/* ---------------- 带外凭据 ---------------- */

export function fetchBmcCredentials() {
  return http.get<any, BmcCredentialItem[]>('/discovery/credentials')
}

export function createBmcCredential(payload: BmcCredentialCreatePayload) {
  return http.post<any, BmcCredentialItem>('/discovery/credentials', payload)
}

/* ---------------- 带外端点 ---------------- */

export function fetchBmcEndpoints(params: BmcEndpointQuery) {
  return http.get<any, PageResult<BmcEndpointItem>>('/oob/endpoints', { params })
}

export function createBmcEndpoint(payload: BmcEndpointUpsertPayload) {
  return http.post<any, BmcEndpointItem>('/oob/endpoints', payload)
}

export function updateBmcEndpoint(id: number, payload: BmcEndpointUpsertPayload) {
  return http.put<any, BmcEndpointItem>(`/oob/endpoints/${id}`, payload)
}

export function deleteBmcEndpoint(id: number) {
  return http.delete<any, boolean>(`/oob/endpoints/${id}`)
}

/** 批量导入（部分成功） */
export function importBmcEndpoints(rows: EndpointImportRow[]) {
  return http.post<any, EndpointImportResult>('/oob/endpoints/import', rows)
}

/** 立即采集（未纳管/停用后端 400） */
export function collectEndpointNow(id: number) {
  return http.post<any, boolean>(`/oob/endpoints/${id}/collect-now`)
}

export function fetchEndpointSnapshots(id: number, params: { page: number; size: number }) {
  return http.get<any, PageResult<HwSnapshotItem>>(`/oob/endpoints/${id}/snapshots`, { params })
}
