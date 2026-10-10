import http from './client'
import type { CsvImportResult, PageResult } from './types'

/* ---------------- 物料 ---------------- */

/** 物料条目（stock/totalIn/totalOut 由服务端维护，创建/编辑不可传） */
export interface MaterialItem {
  materialCode: string
  name: string
  category?: string
  type?: string
  brand?: string
  model?: string
  pn?: string
  spec?: string
  unit: string
  stock: number
  safetyStock?: number
  maxStock?: number
  totalIn: number
  totalOut: number
  price?: number
  location?: string
  /** 默认货位（V15 分仓，三级） */
  warehouseCode?: string
  shelfCode?: string
  layerCode?: string
  /** 分仓现存量 {仓库编码: qty}，由服务端批量补充 */
  warehouseStocks?: Record<string, number>
  supplierCode?: string
  remark?: string
  version?: number
  createdAt?: string
  updatedAt?: string
}

/** 物料列表查询参数 */
export interface MaterialQuery {
  page: number
  size: number
  keyword?: string
  category?: string
}

/** 物料元数据（单位 / 分类下拉） */
export interface MaterialMeta {
  units: string[]
  categories: string[]
}

/** 新增 / 编辑物料公共字段 */
export interface MaterialPayload {
  name: string
  unit: string
  category?: string
  type?: string
  brand?: string
  model?: string
  pn?: string
  spec?: string
  safetyStock?: number
  maxStock?: number
  price?: number
  location?: string
  warehouseCode?: string
  shelfCode?: string
  layerCode?: string
  remark?: string
}

/** 新增物料请求体（materialCode 创建后不可修改） */
export interface MaterialCreatePayload extends MaterialPayload {
  materialCode: string
}

/* ---------------- 出入库 ---------------- */

/** 出入库类型：IN 入库 / OUT 出库 / ADJUST 调整 / TRANSFER 转仓 */
export type StockLogType = 'IN' | 'OUT' | 'ADJUST' | 'TRANSFER'

/** 出入库流水条目 */
export interface StockLogItem {
  id: number | string
  logNo: string
  logType: StockLogType
  materialCode: string
  qty: number
  bizDate?: string
  operator?: string
  department?: string
  refWo?: string
  /** 发生仓（TRANSFER 时为目标仓） */
  warehouseCode?: string
  /** 仅 TRANSFER 的源仓 */
  fromWarehouseCode?: string
  remark?: string
  createdAt?: string
}

/** 出入库流水查询参数 */
export interface StockLogQuery {
  page: number
  size: number
  logType?: StockLogType | ''
  materialCode?: string
  warehouseCode?: string
  bizDateFrom?: string
  bizDateTo?: string
  keyword?: string
}

/** 物料下拉选项（出入库远程搜索） */
export interface MaterialOption {
  materialCode: string
  name: string
  unit: string
  stock: number
  warehouseCode?: string
  warehouseStocks?: Record<string, number>
}

/** 单条出入库请求体（ADJUST 的 qty 为校准后目标库存；TRANSFER 必须给 fromWarehouseCode + warehouseCode） */
export interface StockLogPayload {
  logType: StockLogType
  materialCode: string
  qty: number
  bizDate?: string
  department?: string
  refWo?: string
  remark?: string
  warehouseCode?: string
  fromWarehouseCode?: string
}

/** 批量出入库结果 */
export interface BatchStockLogResult {
  count: number
  logs: {
    logNo: string
    materialCode: string
    logType: StockLogType
    qty: number
  }[]
}

/* ---------------- 盘点 ---------------- */

/** 盘点任务筛选条件（各字段均可空 = 不限；warehouseCodes/materialCodes 仅物料盘点） */
export interface InventoryFilter {
  regionCodes?: string[]
  roomCodes?: string[]
  categoryCodes?: string[]
  statusBiz?: string[]
  warehouseCodes?: string[]
  materialCodes?: string[]
}

/** 盘点对象：ASSET 资产 / MATERIAL 物料（V15 新增） */
export type InventoryTargetType = 'ASSET' | 'MATERIAL'

/** 盘点任务条目（status：盘点中 | 已完成 | 已取消） */
export interface InventoryTaskItem {
  taskNo: string
  name: string
  scope?: string
  filter?: InventoryFilter | null
  targetType?: InventoryTargetType
  inspector: string
  status: string
  totalCount: number
  checkedCount: number
  diffCount: number
  creator?: string
  planFinish?: string
  finishTime?: string
  createdAt?: string
}

/** 盘点任务列表查询参数 */
export interface InventoryTaskQuery {
  page: number
  size: number
  status?: string
  keyword?: string
}

/** 创建盘点任务请求体（无匹配资产/物料时后端 400） */
export interface InventoryTaskCreatePayload {
  name: string
  filter: InventoryFilter
  inspector: string
  planFinish?: string
  scope?: string
  targetType?: InventoryTargetType
}

/** 盘点明细条目（result：一致 | 盘亏 | 盘盈 | 错位 | 未盘） */
export interface InventoryItemDetail {
  id: number | string
  itemNo: string
  taskNo: string
  assetNo?: string
  /** 物料盘点对象 */
  materialCode?: string
  warehouseCode?: string
  /** 实盘数量（物料任务必填；空表示未盘） */
  countedQty?: number
  expected?: {
    rackCode?: string
    unitNo?: string
    uHeight?: number
    statusBiz?: string
    sn?: string
    categoryCode?: string
    modelCode?: string
    qty?: number
  } | null
  result: string
  checkedAt?: string
  checker?: string
  diffNote?: string
}

/** 盘点明细列表查询参数 */
export interface InventoryItemQuery {
  page: number
  size: number
  result?: string
}

/** 录入盘点结果请求体（物料任务必填 countedQty；result 可省略由后端推导） */
export interface InventoryItemResultPayload {
  result?: string
  diffNote?: string
  countedQty?: number
}

/* ---------------- 物料 ---------------- */

export function fetchMaterials(params: MaterialQuery) {
  return http.get<any, PageResult<MaterialItem>>('/materials', { params })
}

export function fetchMaterialMeta() {
  return http.get<any, MaterialMeta>('/materials/meta')
}

export function createMaterial(payload: MaterialCreatePayload) {
  return http.post<any, void>('/materials', payload)
}

export function updateMaterial(materialCode: string, payload: MaterialPayload) {
  return http.put<any, void>(`/materials/${encodeURIComponent(materialCode)}`, payload)
}

export function deleteMaterial(materialCode: string) {
  return http.delete<any, void>(`/materials/${encodeURIComponent(materialCode)}`)
}

/** 物料 CSV 导入模板 */
export async function fetchMaterialImportTemplate() {
  const res = await http.get<Blob>('/materials/template', { responseType: 'blob' })
  return res.data
}

/** 物料 CSV 批量导入 */
export function importMaterials(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<FormData, CsvImportResult>('/materials/import', form)
}

/* ---------------- 出入库 ---------------- */

export function fetchStockLogs(params: StockLogQuery) {
  return http.get<any, PageResult<StockLogItem>>('/stock-logs', { params })
}

export function fetchMaterialOptions(params: { keyword?: string; size?: number } = {}) {
  return http.get<any, MaterialOption[]>('/stock-logs/materials-options', { params })
}

export function createStockLog(payload: StockLogPayload) {
  return http.post<any, void>('/stock-logs', payload)
}

export function createStockLogsBatch(payload: { items: StockLogPayload[] }) {
  return http.post<any, BatchStockLogResult>('/stock-logs/batch', payload)
}

/* ---------------- 盘点 ---------------- */

export function fetchInventoryTasks(params: InventoryTaskQuery) {
  return http.get<any, PageResult<InventoryTaskItem>>('/inventory-tasks', { params })
}

export function createInventoryTask(payload: InventoryTaskCreatePayload) {
  return http.post<any, void>('/inventory-tasks', payload)
}

export function fetchInventoryTaskDetail(taskNo: string) {
  return http.get<any, InventoryTaskItem>(`/inventory-tasks/${encodeURIComponent(taskNo)}`)
}

export function fetchInventoryItems(taskNo: string, params: InventoryItemQuery) {
  return http.get<any, PageResult<InventoryItemDetail>>(
    `/inventory-tasks/${encodeURIComponent(taskNo)}/items`,
    { params },
  )
}

export function updateInventoryItem(
  taskNo: string,
  itemNo: string,
  payload: InventoryItemResultPayload,
) {
  return http.put<any, InventoryItemDetail>(
    `/inventory-tasks/${encodeURIComponent(taskNo)}/items/${encodeURIComponent(itemNo)}`,
    payload,
  )
}

export function completeInventoryTask(taskNo: string) {
  return http.post<any, void>(`/inventory-tasks/${encodeURIComponent(taskNo)}/complete`)
}

export function cancelInventoryTask(taskNo: string) {
  return http.post<any, void>(`/inventory-tasks/${encodeURIComponent(taskNo)}/cancel`)
}

/* ---------------- 仓库 / 货架 / 层位 ---------------- */

/** 仓库状态（字典 warehouse_type.status，取值与既有枚举一致） */
export type WarehouseStatus = '正常' | '停用'

/** 仓库条目（shelfCount/layerCount/materialCount/stockQty 由服务端聚合） */
export interface WarehouseItem {
  warehouseCode: string
  name: string
  warehouseType?: string
  roomCode?: string
  address?: string
  manager?: string
  capacityDesc?: string
  status: WarehouseStatus
  isDefault: boolean
  remark?: string
  version: number
  shelfCount: number
  layerCount: number
  materialCount: number
  stockQty: number
  createdAt?: string
  updatedAt?: string
}

/** 仓库列表查询参数 */
export interface WarehouseQuery {
  page: number
  size: number
  keyword?: string
  status?: WarehouseStatus | ''
}

/** 仓库元数据（类型 / 状态 / 货架类型 / 编码规则） */
export interface WarehouseMeta {
  warehouseTypes: string[]
  statuses: string[]
  shelfTypes: string[]
  layerStatuses: string[]
  codeRules: { warehouse: string; shelf: string; layer: string }
}

/** 仓库下拉项（默认仓排最前） */
export interface WarehouseOption {
  warehouseCode: string
  name: string
  isDefault: boolean
  status: string
}

/** 层位条目（编号只自动生成 LY-###） */
export interface WarehouseLayerItem {
  layerCode: string
  shelfCode: string
  layerNo: number
  name?: string
  capacityDesc?: string
  status: string
  remark?: string
  version?: number
}

/** 货架条目（layers 为该货架下全部层位） */
export interface WarehouseShelfItem {
  shelfCode: string
  warehouseCode: string
  name?: string
  shelfType: string
  layerCount: number
  status: string
  remark?: string
  version: number
  layers: WarehouseLayerItem[]
  layerTotal: number
}

/** 某仓库的库存明细行（含低储预警） */
export interface WarehouseStockItem {
  materialCode: string
  name: string
  category?: string
  unit: string
  qty: number
  safetyStock?: number
  maxStock?: number
  price?: number
  defaultWarehouse?: string
  shelfCode?: string
  layerCode?: string
  alert?: 'LOW' | 'OUT' | null
}

/** 新增 / 编辑仓库公共字段（编号仅创建时可给，空则发 WH-###） */
export interface WarehousePayload {
  warehouseCode?: string
  name: string
  warehouseType?: string
  roomCode?: string
  address?: string
  manager?: string
  capacityDesc?: string
  status?: string
  isDefault?: boolean
  remark?: string
  version?: number
}

/** 新增 / 编辑货架（layerCount 扩容补层位、缩容守卫） */
export interface ShelfPayload {
  shelfCode?: string
  name?: string
  shelfType?: string
  layerCount?: number
  status?: string
  remark?: string
  version?: number
}

/** 新增 / 编辑层位（编号自动生成，层序号不可改） */
export interface LayerPayload {
  name?: string
  capacityDesc?: string
  status?: string
  remark?: string
  version?: number
}

export function fetchWarehouses(params: WarehouseQuery) {
  return http.get<any, PageResult<WarehouseItem>>('/warehouses', { params })
}

export function fetchWarehouseMeta() {
  return http.get<any, WarehouseMeta>('/warehouses/meta')
}

export function fetchWarehouseOptions(withDisabled = false) {
  return http.get<any, WarehouseOption[]>('/warehouses/options', { params: { withDisabled } })
}

export function createWarehouse(payload: WarehousePayload) {
  return http.post<any, WarehouseItem>('/warehouses', payload)
}

export function updateWarehouse(warehouseCode: string, payload: WarehousePayload) {
  return http.put<any, WarehouseItem>(`/warehouses/${encodeURIComponent(warehouseCode)}`, payload)
}

export function deleteWarehouse(warehouseCode: string) {
  return http.delete<any, void>(`/warehouses/${encodeURIComponent(warehouseCode)}`)
}

export function fetchShelves(warehouseCode: string) {
  return http.get<any, WarehouseShelfItem[]>(`/warehouses/${encodeURIComponent(warehouseCode)}/shelves`)
}

export function createShelf(warehouseCode: string, payload: ShelfPayload) {
  return http.post<any, WarehouseShelfItem>(
    `/warehouses/${encodeURIComponent(warehouseCode)}/shelves`,
    payload,
  )
}

export function updateShelf(shelfCode: string, payload: ShelfPayload) {
  return http.put<any, WarehouseShelfItem>(`/warehouses/shelves/${encodeURIComponent(shelfCode)}`, payload)
}

export function deleteShelf(shelfCode: string) {
  return http.delete<any, void>(`/warehouses/shelves/${encodeURIComponent(shelfCode)}`)
}

export function fetchLayerOptions(shelfCode?: string) {
  return http.get<any, WarehouseLayerItem[]>('/warehouses/layers/options', {
    params: shelfCode ? { shelfCode } : {},
  })
}

export function createLayer(shelfCode: string, payload: LayerPayload) {
  return http.post<any, WarehouseLayerItem>(
    `/warehouses/shelves/${encodeURIComponent(shelfCode)}/layers`,
    payload,
  )
}

export function updateLayer(layerCode: string, payload: LayerPayload) {
  return http.put<any, WarehouseLayerItem>(`/warehouses/layers/${encodeURIComponent(layerCode)}`, payload)
}

export function deleteLayer(layerCode: string) {
  return http.delete<any, void>(`/warehouses/layers/${encodeURIComponent(layerCode)}`)
}

export function fetchWarehouseStock(warehouseCode: string) {
  return http.get<any, WarehouseStockItem[]>(
    `/warehouses/${encodeURIComponent(warehouseCode)}/stock`,
  )
}
