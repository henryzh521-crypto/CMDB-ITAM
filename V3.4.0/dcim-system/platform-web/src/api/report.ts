import http from './client'

/** 分组计数行（label=key，即库中状态值/编码本身） */
export interface GroupCountItem {
  key: string
  label: string
  count: number
}

/** 分组计数 + 金额合计行（资产分组报表） */
export interface GroupValueItem extends GroupCountItem {
  totalValue: number
}

/** 机房 U 位占用（dashboard byRoomUsage；rate = 占用/容量，0-1 保留 4 位） */
export interface RoomUsageItem {
  key: string
  label: string
  uCap: number
  uOcc: number
  rate: number
}

/** 分类 × 业务状态交叉计数（dashboard categoryStatus，堆叠柱用） */
export interface CategoryStatusItem {
  category: string
  statusBiz: string
  count: number
}

/** 运营总览仪表盘聚合数据（一次请求喂满 9 张图 + 4 张 KPI 卡） */
export interface DashboardData {
  assetTotal: number
  byStatusBiz: GroupCountItem[]
  byRegion: GroupCountItem[]
  byCategory: GroupCountItem[]
  rackUsage: {
    totalUnits: number
    usedUnits: number
    /** 0-1，保留 4 位 */
    usageRate: number
  }
  woStats: {
    total: number
    pending: number
    approving: number
    processing: number
    done: number
    rejected: number
    closed: number
    slaOverdue: number
  }
  categoryStatus: CategoryStatusItem[]
  byRoomUsage: RoomUsageItem[]
  byEventType: GroupCountItem[]
  /** 近 12 个自然月上架数（空月补 0，key=YYYY-MM） */
  trendRacked: GroupCountItem[]
  /** 近 12 个自然月采购到货数（空月补 0） */
  trendPurchased: GroupCountItem[]
  /** 近 30 天新增上架数 */
  recent30dRacked: number
  /** 到期资产数（含已过期） */
  expiryCount?: number
  /** 30天内到期资产数 */
  expiry30d?: number
  /** 预计达到 85% 容量的月数 */
  forecastMonths?: number
}

/** 工单 SLA 报表（complianceRate 无工单数据时为空） */
export interface SlaData {
  total: number
  respondedCount: number
  responseRate: number
  overdueCount: number
  complianceRate?: number | null
}

/** 物料库存明细行（alert 缺省 = 正常） */
export interface WarehouseItem {
  materialCode: string
  name: string
  category?: string
  unit?: string
  stock: number
  safetyStock: number
  maxStock: number
  totalIn: number
  totalOut: number
  stockValue: number
  alert?: 'LOW' | 'OUT' | null
  /** 默认仓库（V15 分仓） */
  warehouseCode?: string
}

/** 仓库库存聚合行（按 stock_level 实际分仓；empty = 该仓无货） */
export interface WarehouseAggItem {
  warehouseCode: string
  name: string
  warehouseType?: string
  status: string
  materialCount: number
  stockQty: number
  stockValue: number
  lowCount: number
  outCount: number
  empty: boolean
}

/** 仓储报表 */
export interface WarehouseData {
  items: WarehouseItem[]
  summary: {
    materialCount: number
    totalStockValue: number
    lowStockCount: number
    outOfStockCount: number
  }
  /** 按仓库分组维度 */
  byWarehouse: WarehouseAggItem[]
}

/** 资产分组维度：业务状态/区域/分类/权属 */
export type AssetGroupBy = 'statusBiz' | 'regionCode' | 'categoryCode' | 'ownerType'

/** 工单分组维度：状态/类型/优先级/根因 */
export type WorkorderGroupBy = 'status' | 'woType' | 'priority' | 'rootCause'

/* ───────────── 维修分析（时长 / 趋势 / 备件消耗 / 费用） ───────────── */

/** 维修时长分析（三组时长分位 + 响应/根因填写率；无样本的时长字段缺省） */
export interface RepairDurationData {
  from: string
  to: string
  /** 区间内维修类工单数（不含变更等非维修类型） */
  woCount: number
  doneCount: number
  rootCauseCount: number
  /** 已完工单的根因填写率（0-1） */
  rootCauseFillRate: number
  sampleCount: number
  avgHours?: number | null
  medianHours?: number | null
  p90Hours?: number | null
  minHours?: number | null
  maxHours?: number | null
  repairSampleCount: number
  avgRepairHours?: number | null
  medianRepairHours?: number | null
  responseSampleCount: number
  avgResponseHours?: number | null
  medianResponseHours?: number | null
  respondRate: number
  /** 根因分布（不含「未填写」） */
  rootCauses: GroupCountItem[]
}

/** 趋势数据点（空桶 count=0；桶内无完工单时 avgHours 缺省） */
export interface RepairTrendPoint {
  bucket: string
  count: number
  doneCount: number
  avgHours?: number | null
}

/** 备件消耗汇总（stock_log.ref_wo 关联，金额 = qty × material.price） */
export interface WoConsumptionSummary {
  woCount: number
  materialCount: number
  totalQty: number
  totalValue: number
}

/** 按工单的备件消耗行 */
export interface WoConsumptionRow {
  woNo: string
  woType: string
  title: string
  status: string
  logCount: number
  materialCount: number
  qty: number
  value: number
}

/** 按物料的备件消耗行 */
export interface MaterialConsumptionRow {
  materialCode: string
  name: string
  unit?: string
  qty: number
  value: number
  /** 领用该物料的工单数 */
  woCount: number
}

export interface RepairMaterialsData {
  from: string
  to: string
  summary: WoConsumptionSummary
  byWo: WoConsumptionRow[]
  byMaterial: MaterialConsumptionRow[]
}

/** 维修费用汇总（人工/外委/其他/备件 四类） */
export interface RepairCostSummary {
  woCount: number
  laborValue: number
  serviceValue: number
  otherValue: number
  partsValue: number
  partsWoCount: number
}

/** 按工单的维修费用行（合计倒序） */
export interface RepairCostRow {
  woNo: string
  woType: string
  title: string
  status: string
  laborValue: number
  serviceValue: number
  otherValue: number
  partsValue: number
  totalValue: number
}

export interface RepairCostData {
  from: string
  to: string
  summary: RepairCostSummary
  byWo: RepairCostRow[]
}

/** 运营总览仪表盘（一次聚合） */
export function fetchDashboard() {
  return http.get<any, DashboardData>('/report/dashboard')
}

/** 资产分组报表：计数 + 采购金额合计 */
export function fetchAssetReport(groupBy: AssetGroupBy) {
  return http.get<any, GroupValueItem[]>('/report/assets', { params: { groupBy } })
}

/** 工单分组报表：状态/类型/优先级计数 */
export function fetchWorkorderReport(groupBy: WorkorderGroupBy) {
  return http.get<any, GroupCountItem[]>('/report/workorders', { params: { groupBy } })
}

/** 工单 SLA 报表：响应率/达标率/超期数 */
export function fetchWorkorderSla() {
  return http.get<any, SlaData>('/report/workorders/sla')
}

/** 仓储报表：物料库存明细 + 汇总 + 按仓库分组（warehouseCode 可空 = 全部） */
export function fetchWarehouseReport(warehouseCode?: string) {
  return http.get<any, WarehouseData>('/report/warehouse', {
    params: warehouseCode ? { warehouseCode } : {},
  })
}

/** 维修时长分析：三组时长分位 + 响应率 + 根因填写率（from/to = yyyy-MM-dd，缺省走后端默认区间） */
export function fetchRepairDuration(from?: string, to?: string) {
  return http.get<any, RepairDurationData>('/report/workorders/repair/duration', {
    params: { from, to },
  })
}

/** 维修工单时间趋势（granularity: day | week | month，空桶补 0） */
export function fetchRepairTrend(
  from?: string,
  to?: string,
  granularity: 'day' | 'week' | 'month' = 'month',
) {
  return http.get<any, RepairTrendPoint[]>('/report/workorders/repair/trend', {
    params: { from, to, granularity },
  })
}

/** 备件消耗：按工单 + 按物料（stock_log.ref_wo 口径） */
export function fetchRepairMaterials(from?: string, to?: string) {
  return http.get<any, RepairMaterialsData>('/report/workorders/repair/materials', {
    params: { from, to },
  })
}

/** 维修费用：四类合计 + 按单费用榜 */
export function fetchRepairCost(from?: string, to?: string) {
  return http.get<any, RepairCostData>('/report/workorders/repair/cost', { params: { from, to } })
}

/** 资产财务价值汇总（原值/折旧/净值/减值 合计 + 分类明细） */
export interface AssetFinanceSummary {
  totalOriginValue: number
  totalAccumulatedDep: number
  totalNetValue: number
  totalImpairment: number
  byCategory: {
    category: string
    originValue: number
    accumulatedDep: number
    netValue: number
    impairment: number
    count: number
  }[]
}

/** 获取资产财务价值汇总 */
export function fetchAssetFinanceSummary() {
  return http.get<any, AssetFinanceSummary>('/report/asset/finance/summary', { silent: true })
}

/** 运维成本结构分析（电力/维修/人力/备件/折旧） */
export interface OpexStructureData {
  total: number
  breakdown: {
    name: string
    value: number
    percent: number
  }[]
  monthlyTrend: {
    month: string
    power: number
    maintenance: number
    labor: number
    parts: number
    depreciation: number
  }[]
}

/** 获取运维成本结构分析 */
export function fetchOpexStructure() {
  return http.get<any, OpexStructureData>('/report/opex/structure', { silent: true })
}

/** 资产投资回报分析（采购成本 vs 产出价值/利用率） */
export interface AssetRoiData {
  byCategory: {
    category: string
    purchaseCost: number
    currentValue: number
    utilizationRate: number
    roi: number
    paybackMonths: number
  }[]
  summary: {
    totalInvestment: number
    totalCurrentValue: number
    avgUtilization: number
    avgRoi: number
  }
}

/** 获取资产投资回报分析 */
export function fetchAssetRoi() {
  return http.get<any, AssetRoiData>('/report/asset/roi', { silent: true })
}

/** 供应商绩效分析（交付及时率/质量/响应/成本） */
export interface SupplierPerformanceData {
  suppliers: {
    supplierId: string
    supplierName: string
    /** 平台未记录交付时间时为 null */
    deliveryOnTimeRate: number | null
    /** 1 - 所供资产近 12 个月故障率；无关联资产时为 null */
    qualityPassRate: number | null
    /** 平台未记录供应商响应时间时为 null */
    avgResponseHours: number | null
    totalContracts: number
    totalAmount: number
    score: number | null
  }[]
}

/** 获取供应商绩效分析 */
export function fetchSupplierPerformance() {
  return http.get<any, SupplierPerformanceData>('/report/supplier/performance', { silent: true })
}

/** 容量规划：机柜/功率/制冷 多维预测 */
export interface CapacityPlanningData {
  rack?: {
    currentUsage: number
    totalCapacity: number
    forecast: { month: string; predictedUsage: number; predictedRate?: number; threshold: number }[]
  }
  power?: {
    currentUsage: number
    totalCapacity: number
    forecast: { month: string; predictedUsage: number; threshold: number }[]
  }
  /** 平台不采集制冷容量时缺省 */
  cooling?: {
    currentUsage: number
    totalCapacity: number
    forecast: { month: string; predictedUsage: number; threshold: number }[]
  }
}

/** 获取容量规划预测 */
export function fetchCapacityPlanning() {
  return http.get<any, CapacityPlanningData>('/report/capacity/planning', { silent: true })
}

/** 资产生命周期成本分析（TCO） */
export interface AssetTcoData {
  byCategory: {
    category: string
    acquisitionCost: number
    maintenanceCost: number
    energyCost: number
    depreciation: number
    disposalCost: number
    totalTco: number
    avgAnnualTco: number
  }[]
  summary: {
    totalTco: number
    avgAnnualTco: number
  }
}

/** 获取资产全生命周期成本分析 */
export function fetchAssetTco() {
  return http.get<any, AssetTcoData>('/report/asset/tco', { silent: true })
}
