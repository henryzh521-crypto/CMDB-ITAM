import http from './client'
import type { CsvImportResult, PageResult } from './types'

/** 机房条目 */
export interface RoomItem {
  roomCode: string
  name: string
  dataCenter?: string
  /** 区域编码 QY/WLCB/SG */
  regionCode?: string
  city?: string
  location?: string
  areaSqm?: number
  loadKg?: number
  coolingKw?: number
  powerKw?: number
  cabinetCount?: number
  abPath?: string
  envMonitor?: string
  manager?: string
  remark?: string
}

/** 机房列表查询参数 */
export interface RoomQuery {
  page: number
  size: number
  keyword?: string
}

/** 新增机房请求体（roomCode 创建后不可修改） */
export interface RoomCreatePayload {
  roomCode: string
  name: string
  dataCenter?: string
  regionCode?: string
  city?: string
  location?: string
  areaSqm?: number
  loadKg?: number
  coolingKw?: number
  powerKw?: number
  abPath?: string
  envMonitor?: string
  manager?: string
  remark?: string
}

/** 编辑机房请求体（不含 roomCode） */
export type RoomUpdatePayload = Omit<RoomCreatePayload, 'roomCode'>

/** 机柜条目 */
export interface RackItem {
  rackCode: string
  roomCode: string
  locationName?: string
  rowNo?: number
  colNo?: number
  orientation?: string
  uCapacity: number
  powerLimitKw?: number
  pduCount?: number
  coolingType?: string
  manager?: string
  remark?: string
  usedUnits?: number
}

/** 机柜列表查询参数 */
export interface RackQuery {
  page: number
  size: number
  keyword?: string
  roomCode?: string
}

/** 新增机柜请求体（创建后自动生成 U 位） */
export interface RackCreatePayload {
  rackCode: string
  roomCode: string
  locationName?: string
  rowNo?: number
  colNo?: number
  orientation?: string
  uCapacity?: number
  powerLimitKw?: number
  pduCount?: number
  coolingType?: string
  manager?: string
  remark?: string
}

/** 编辑机柜请求体（不含 rackCode） */
export type RackUpdatePayload = Omit<RackCreatePayload, 'rackCode'>

/** U 位状态：空闲 | 占用 | 预留 | 故障 */
export interface RackUnit {
  unitNo: number
  status: string
  occupiedBy?: string
  assetName?: string
  assetStatusBiz?: string
}

/** 机柜 U 位平面图数据 */
export interface RackUnits {
  rackCode: string
  uCapacity: number
  units: RackUnit[]
}

/** 可用起始 U 位查询参数 */
export interface FreeUnitsQuery {
  uHeight?: number
  excludeAssetNo?: string
}

/* ---------------- 机房平面图（2D/3D 展示） ---------------- */

/** 平面图机柜条目（含行列网格位与占用统计） */
export interface FloorRack {
  rackCode: string
  roomCode: string
  rowNo?: string | number | null
  colNo?: number | null
  /** 布局行（rowNo 归一化，缺省为空串） */
  effRow?: string | number | null
  /** 布局列（colNo 缺省时按排内序号回填） */
  effCol?: number
  locationName?: string
  orientation?: string
  uCapacity: number
  usedUnits: number
  freeUnits: number
  /** 0~1，四位小数 */
  usageRate: number
  powerLimitKw?: number
  pduCount?: number
  coolingType?: string
  manager?: string
}

/** 平面图聚合统计 */
export interface FloorStats {
  rackCount: number
  totalUnits: number
  usedUnits: number
  freeUnits: number
  usageRate: number
}

/** GET /rooms/{roomCode}/floor 响应 */
export interface RoomFloor {
  room: RoomItem
  stats: FloorStats
  racks: FloorRack[]
}

/** 机房平面图聚合（机房 + 全部机柜 + 占用统计，供 2D/3D 渲染） */
export function fetchRoomFloor(roomCode: string) {
  return http.get<any, RoomFloor>(`/rooms/${encodeURIComponent(roomCode)}/floor`)
}

/* ---------------- 机房 ---------------- */

export function fetchRooms(params: RoomQuery) {
  return http.get<any, PageResult<RoomItem>>('/rooms', { params })
}

export function createRoom(payload: RoomCreatePayload) {
  return http.post<any, void>('/rooms', payload)
}

export function updateRoom(roomCode: string, payload: RoomUpdatePayload) {
  return http.put<any, void>(`/rooms/${encodeURIComponent(roomCode)}`, payload)
}

export function deleteRoom(roomCode: string) {
  return http.delete<any, void>(`/rooms/${encodeURIComponent(roomCode)}`)
}

/** 机房 CSV 导入模板 */
export async function fetchRoomImportTemplate() {
  const res = await http.get<Blob>('/rooms/template', { responseType: 'blob' })
  return res.data
}

/** 机房 CSV 批量导入（部分成功） */
export function importRooms(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<FormData, CsvImportResult>('/rooms/import', form)
}

/* ---------------- 机柜 ---------------- */

export function fetchRacks(params: RackQuery) {
  return http.get<any, PageResult<RackItem>>('/racks', { params })
}

export function createRack(payload: RackCreatePayload) {
  return http.post<any, void>('/racks', payload)
}

export function updateRack(rackCode: string, payload: RackUpdatePayload) {
  return http.put<any, void>(`/racks/${encodeURIComponent(rackCode)}`, payload)
}

export function deleteRack(rackCode: string) {
  return http.delete<any, void>(`/racks/${encodeURIComponent(rackCode)}`)
}

/** 机柜 U 位平面图（unitNo 降序） */
export function fetchRackUnits(rackCode: string) {
  return http.get<any, RackUnits>(`/racks/${encodeURIComponent(rackCode)}/units`)
}

/** 可用起始 U 位（降序），uHeight 按设备高度过滤，excludeAssetNo 编辑时排除自身占用 */
export function fetchRackFreeUnits(rackCode: string, params: FreeUnitsQuery = {}) {
  return http.get<any, number[]>(`/racks/${encodeURIComponent(rackCode)}/free-units`, {
    params,
  })
}

/** 机柜 CSV 导入模板 */
export async function fetchRackImportTemplate() {
  const res = await http.get<Blob>('/racks/template', { responseType: 'blob' })
  return res.data
}

/** 机柜 CSV 批量导入（部分成功） */
export function importRacks(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<FormData, CsvImportResult>('/racks/import', form)
}
