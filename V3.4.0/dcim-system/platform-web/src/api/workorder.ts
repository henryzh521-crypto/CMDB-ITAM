import http from './client'
import type { PageResult } from './types'

/** 工单 11 类业务类型（后端 ModuleRegistry module = workorders；权威在 workflow_config 表） */
export const WO_TYPES = [
  '故障维修',
  '部件更换',
  '变更',
  '巡检',
  '入库验收',
  '资产盘点',
  '耗材申领',
  '上架部署',
  '下架退库',
  '报废处置',
  '调拨',
] as const

/** 工单状态 */
export const WO_STATUSES = ['待处理', '待审批', '处理中', '已完成', '已驳回', '已关闭'] as const

/** 工单优先级 */
export const WO_PRIORITIES = ['高', '中', '低'] as const

/** 支持子件更换明细的工单类型（与后端 SWAP_WO_TYPES 对齐） */
export const SWAP_WO_TYPES = ['故障维修', '部件更换'] as const

/** 维修类工单类型（时长/趋势/费用/根因的统一口径，与后端 ReportQueryMapper.REPAIR_WO_TYPES 对齐；与换件类型同集） */
export const REPAIR_WO_TYPES = SWAP_WO_TYPES

/** 换下件允许状态（按换件原因区分；与后端 SWAP_OUT_STATUSES 对齐，空 = 维修中） */
export const SWAP_OUT_STATUSES = ['维修中', '停用中', '待退役'] as const

/** 流转记录 */
export interface WoFlow {
  seq: number
  action: string
  from?: string
  to?: string
  actor?: string
  comment?: string
  createdAt?: string
}

/** 子件更换明细行（oldStatus 空 = 执行时换下为「维修中」） */
export interface WoReplacement {
  parentAssetNo: string
  oldAssetNo: string
  newAssetNo: string
  /** 旧件换下后状态：维修中 / 停用中 / 待退役（须从旧件当前态可达） */
  oldStatus?: string
}

/** 工单列表条目（relatedAssets/relatedMaterials 为逗号分隔字符串） */
export interface WorkorderItem {
  woNo: string
  woType: string
  title: string
  description?: string
  status: string
  priority?: string
  applicant?: string
  applicantUsername?: string
  handler?: string
  relatedAssets?: string
  relatedMaterials?: string
  applyQty?: number
  targetLocation?: string
  targetRack?: string
  targetUnit?: number
  expectFinish?: string
  applyTime?: string
  finishTime?: string
  process?: string
  completeNote?: string
  rootCause?: string
  /** 维修费用（仅故障维修/部件更换，其余类型恒为 0） */
  costLabor?: number
  costService?: number
  costOther?: number
  slaResponseDeadline?: string
  slaResolveDeadline?: string
  /** SLA 是否已超期 —— 由后端 SlaPolicy 统一计算下发，前端不再各算各的 */
  slaOverdue?: boolean
  firstResponseAt?: string
  slaEscalations?: number
  lastEscalationAt?: string
  approvalLevel?: number
  autoDeducted?: boolean
  version?: number
  /** 父工单号（故障维修/部件更换替换子单指向父单） */
  parentWo?: string
  /** 被更换的旧部件/资产编号 */
  replacedAsset?: string
  /** 子件更换明细（故障维修/部件更换） */
  replacements?: WoReplacement[]
  /** 调拨源位置 JSON 快照（创建时写入） */
  beforeLocation?: string
  createdAt?: string
  updatedAt?: string
}

/** 单个状态机动作的权限元数据（P1「动作即权限」：只渲染 visible=true 的按钮） */
export interface WoActionMeta {
  action: string
  /** 危险动作（驳回/关闭/重新打开/退回）→ 需要二次确认样式 */
  danger?: boolean
  /** 该动作是否要求填写意见（驳回） */
  requiresOpinion?: boolean
  /** 该动作是否要求填写根因（处理中 → 关闭） */
  requiresRootCause?: boolean
  /** 当前登录者是否可执行；false 时不可点，`hint` 为不可执行原因（与 403 文案同源） */
  visible?: boolean
  hint?: string | null
}

/** 详情级操作权限（非状态机动作） */
export interface WoPerm {
  /** 编辑基本字段（标题/描述/优先级/期望完成） */
  edit?: boolean
  /** 新增资产行 */
  addItem?: boolean
  /** 催办（已关闭为 false） */
  remind?: boolean
  /** 评论（已关闭为 false；仍限关联方） */
  comment?: boolean
  /** 上传附件（可见即可传：模块写权限 + update 开关；已关闭为 false，不限关联方） */
  uploadAtt?: boolean
  /** 转办本级审批人（待审批且本人可批） */
  transfer?: boolean
  /** 加签本级审批人（待审批且本人可批） */
  addApprover?: boolean
}

/** 本级委托（转办/加签；level 与 approval_level 相等时生效，detail 按 non_null 下发） */
export interface WoApprovalDelegate {
  level: number
  mode: 'transfer' | 'add'
  /** 中文模式名：转办/加签 */
  modeLabel?: string
  members: string[]
  /** 成员显示名（与 members 同序） */
  memberNames?: string[]
  by?: string
  byName?: string
  opinion?: string
  at?: string
}

/** 工单详情（flows 为顺序流转记录；actions 为状态机全量动作（历史契约）；
 * myActions 为按登录者角色过滤后的可执行动作；childWos 为关联替换子单；items 为资产行） */
export interface WorkorderDetail extends WorkorderItem {
  flows: WoFlow[]
  /** 状态机全量可用动作（与登录者无关，string[] 历史契约） */
  actions: string[]
  /** 按登录者角色过滤后的动作元数据：前端只渲染 visible=true 的项 */
  myActions?: WoActionMeta[]
  /** 详情级操作权限（编辑/加行/催办/评论/附件上传） */
  perm?: WoPerm
  needApproval?: boolean
  approvalRoles?: string[]
  /** 审批链各级中文名（与 approvalRoles 同序） */
  approvalRoleNames?: string[]
  /** 按单指定审批人（二维数组，一级或签；空数组=走角色链） */
  customApprovers?: string[][]
  /** 按单指定审批人显示名（与 customApprovers 同序同形） */
  customApproverNames?: string[][]
  /** 配置层按级指定审批人（与 approvalRoles 下标对齐取并集） */
  approvalUserLevels?: string[][]
  approvalUserLevelNames?: string[][]
  /** 本级委托（转办/加签；仅下发处于当前审批级的委托） */
  delegate?: WoApprovalDelegate | null
  /** 当前审批级别可批人描述（角色名/指定审批人/被转办给谁；无审批链时为 null 不下发） */
  currentLevelName?: string
  childWos?: WorkorderItem[]
  items?: WoAssetItem[]
  /** details JSONB：物料行 {materialCode,qty} 或调拨附加对象 {reason,needDowntime,...} */
  details?: unknown[]
  /** 调拨详情汇总（仅 woType=调拨） */
  transferSummary?: TransferSummary | null
}

/** 工作流配置项 */
export interface WorkflowConfigItem {
  woType: string
  needApproval: boolean
  approvalRoles: string[]
  /** 审批链各级中文名（与 approvalRoles 同序） */
  approvalRoleNames?: string[]
  /** 按级指定审批人用户名（与 approvalRoles 下标对齐取并集；空=不启用） */
  approvalUserLevels?: string[][]
  /** 各状态可用动作：{ 待处理: ['开始处理', ...], ... } */
  actions: Record<string, string[]>
}

/** SLA 配置矩阵行（11 类 × 高/中/低 全量；configured=false 表示该组合未配置） */
export interface SlaConfigItem {
  woType: string
  priority: '高' | '中' | '低'
  responseMinutes?: number | null
  resolveMinutes?: number | null
  escalateTo?: string | null
  escalateToName?: string | null
  configured: boolean
}

/** 工单列表查询参数（tab=approve 表示待我审批） */
export interface WorkorderQuery {
  page: number
  size: number
  status?: string
  woType?: string
  priority?: string
  keyword?: string
  tab?: string
  /** 处理人用户名（模糊） */
  handler?: string
  /** 申请时间起 / 止，yyyy-MM-dd */
  applyFrom?: string
  applyTo?: string
  /** 仅看 SLA 超期 / 未超期（口径与后端 SlaPolicy 一致） */
  slaOverdue?: boolean
}

/** 耗材申领用料明细行 */
export interface WoMaterialDetail {
  materialCode: string
  qty: number
}

/** 调拨目的地机房元数据 */
export interface TransferRoomLocation {
  roomCode: string
  name?: string
  dataCenter?: string | null
  regionCode?: string | null
}

/** 调拨目的地机柜元数据 */
export interface TransferRackLocation {
  rackCode: string
  roomCode: string
  locationName?: string | null
}

/** 调拨目的地（机房 + 机柜） */
export interface TransferLocations {
  rooms: TransferRoomLocation[]
  racks: TransferRackLocation[]
}

/** 调拨范围徽标文案 */
export type TransferScopeLabel = '同机房' | '跨机房' | '跨数据中心' | '跨地域'

/** 调拨详情汇总（后端 transferSummary） */
export interface TransferSummary {
  itemCount: number
  scope?: TransferScopeLabel | null
  needDowntime?: boolean
  /** 到场暂存行数：有目标机房但无目标机柜（完工只迁机房、不占 U，后续再「上架部署」） */
  stagedCount?: number
}

/** 是否「到场暂存」调拨行：有目标机房、无目标机柜 → 完工只迁机房不占 U */
export function isStagedTransfer(woType?: string, row?: { dstRoom?: string | null; dstRack?: string | null }): boolean {
  if (woType !== '调拨' || !row) return false
  return !row.dstRack && !!row.dstRoom
}

/** 工单资产行项目（源位置创建时快照；目标位置仅上架部署/变更/调拨按行填写） */
export interface WoAssetItem {
  itemNo: string
  woNo?: string
  assetNo: string
  srcRoom?: string | null
  srcRack?: string | null
  srcUnit?: number | null
  srcUHeight?: number | null
  srcRegion?: string | null
  dstRoom?: string | null
  dstRack?: string | null
  dstUnit?: number | null
  dstRegion?: string | null
  /** 行级调拨范围（同机房/跨机房/跨数据中心/跨地域；缺位置为 null） */
  scope?: TransferScopeLabel | null
}

/** 创建/维护工单资产行入参 */
export interface WoAssetLineInput {
  assetNo: string
  dstRoom?: string
  dstRack?: string
  dstUnit?: number
}

/** 创建工单请求体 */
export interface WorkorderCreatePayload {
  woType: string
  title: string
  description?: string
  priority?: string
  /** 旧字段：逗号分隔编号列表（兼容；推荐用 assets） */
  relatedAssets?: string[]
  /** 多资产行（推荐；与 relatedAssets 可并存，按 assetNo 合并） */
  assets?: WoAssetLineInput[]
  relatedMaterials?: string[]
  applyQty?: number
  targetRack?: string
  targetUnit?: number
  expectFinish?: string
  details?: Array<WoMaterialDetail | Record<string, unknown>>
  /** 父工单号（创建替换子单时带） */
  parentWo?: string
  /** 被更换的旧部件编号（故障维修/部件更换选填）；有明细时前端由明细派生，不手工填 */
  replacedAsset?: string
  /** 子件更换明细（故障维修/部件更换）；创建时可传，同一父资产可传多行（一台设备换多个部件） */
  replacements?: WoReplacement[]
  /** 按单指定审批人（单级或签用户名列表；空=按 workflow_config 角色链；免审类型后端 400） */
  approvalApprovers?: string[]
}

/** 工单流转请求体（驳回必填 opinion；处理中→关闭必填 rootCause；故障维修/部件更换完成可填 replacedAsset / completeNote / 三笔维修费用） */
export interface WoTransitionPayload {
  action: string
  opinion?: string
  rootCause?: string
  replacedAsset?: string
  /** 完成说明（完成工单选填，落 complete_note，详情可见） */
  completeNote?: string
  /** 维修费用（完成工单且类型为故障维修/部件更换时可传；非维修类型传了后端 400） */
  costLabor?: number
  costService?: number
  costOther?: number
}

/** 维修费用登记/修正请求体（整体覆盖，null 视为 0；结案后仍可改） */
export interface RepairCostPayload {
  costLabor: number
  costService: number
  costOther: number
}

/** 编辑工单基本字段请求体（优先级改动会由后端重算 SLA 时限） */
export interface WoUpdatePayload {
  title: string
  description?: string
  priority?: string
  /** yyyy-MM-dd */
  expectFinish?: string
}

/* ---------------- 工单 ---------------- */

export function fetchWorkorders(params: WorkorderQuery) {
  return http.get<any, PageResult<WorkorderItem>>('/workorders', { params })
}

export function fetchWorkflowConfig() {
  return http.get<any, WorkflowConfigItem[]>('/workorders/workflow-config')
}

/** 调拨目的地元数据（机房 + 机柜，workorders 读权限） */
export function fetchTransferLocations() {
  return http.get<any, TransferLocations>('/workorders/transfer-locations')
}

export function fetchWorkorderDetail(woNo: string) {
  return http.get<any, WorkorderDetail>(`/workorders/${encodeURIComponent(woNo)}`)
}

export function createWorkorder(payload: WorkorderCreatePayload) {
  return http.post<any, { woNo: string }>('/workorders', payload)
}

export function transitionWorkorder(woNo: string, payload: WoTransitionPayload) {
  return http.post<any, void>(`/workorders/${encodeURIComponent(woNo)}/transitions`, payload)
}

/** 登记/修正维修费用（三笔整体覆盖；非维修类型后端 400，结案后仍可改） */
export function updateRepairCost(woNo: string, payload: RepairCostPayload) {
  return http.put<any, void>(`/workorders/${encodeURIComponent(woNo)}/repair-cost`, payload)
}

/** 编辑基本字段（标题/描述/优先级/期望完成）；返回更新后的详情 */
export function updateWorkorder(woNo: string, payload: WoUpdatePayload) {
  return http.put<any, WorkorderDetail>(`/workorders/${encodeURIComponent(woNo)}`, payload)
}

/** 催办：追加 flow 留痕 + 通知相关人；同单 10 分钟内重复催办后端返回 409 */
export function remindWorkorder(woNo: string, opinion?: string) {
  return http.post<any, WorkorderDetail>(
    `/workorders/${encodeURIComponent(woNo)}/remind`,
    opinion ? { opinion } : {},
  )
}

/** 评论：追加 flow 留痕（不改状态、不发通知） */
export function commentWorkorder(woNo: string, content: string) {
  return http.post<any, WorkorderDetail>(
    `/workorders/${encodeURIComponent(woNo)}/comments`,
    { content },
  )
}

/**
 * CSV 导出：参数与列表完全一致（同一套 buildListWrapper），行数封顶 10000。
 * 返回原始 Blob（带 UTF-8 BOM，Excel 打开中文不乱码）。
 */
export async function exportWorkorders(params: WorkorderQuery) {
  const res = await http.get<Blob>('/workorders/export', {
    params,
    responseType: 'blob',
    timeout: 60000,
  })
  return res.data
}

/** SLA 配置矩阵（11 类 × 高/中/低 全量） */
export function fetchSlaConfig() {
  return http.get<any, SlaConfigItem[]>('/workorders/sla-config')
}

/** 新增/修改 SLA 配置（类型 × 优先级 upsert）；仅管理员 */
export function updateSlaConfig(
  woType: string,
  priority: string,
  payload: { responseMinutes?: number; resolveMinutes?: number; escalateTo?: string },
) {
  return http.put<any, SlaConfigItem>(
    `/workorders/sla-config/${encodeURIComponent(woType)}/${encodeURIComponent(priority)}`,
    payload,
  )
}

/** 修改审批链配置（needApproval + 按级角色编码 + 按级指定审批人）；仅管理员 */
export function updateWorkflowConfig(
  woType: string,
  payload: {
    needApproval: boolean
    approvalRoles: string[]
    /** 按级指定审批人（下标对齐 approvalRoles，取并集；空数组=该级不启用） */
    approvalUserLevels?: string[][]
  },
) {
  return http.put<any, WorkflowConfigItem>(
    `/workorders/workflow-config/${encodeURIComponent(woType)}`,
    payload,
  )
}

/**
 * 转办：当前审批级可批人替换为目标用户（仅当前级审批人/管理员；工单须待审批）。
 * 返回详情对象（含更新后的 delegate / perm / currentLevelName）。
 */
export function transferApprover(
  woNo: string,
  payload: { username: string; opinion: string },
) {
  return http.post<any, WorkorderDetail>(
    `/workorders/${encodeURIComponent(woNo)}/approval/transfer`,
    payload,
  )
}

/** 加签：当前审批级追加可批人（并集或签；仅当前级审批人/管理员；工单须待审批） */
export function addApprovers(
  woNo: string,
  payload: { usernames: string[]; opinion?: string },
) {
  return http.post<any, WorkorderDetail>(
    `/workorders/${encodeURIComponent(woNo)}/approval/add`,
    payload,
  )
}

/** 审批人选择器数据源（在职用户，用户名/姓名模糊，上限 50；workorders 只读即可用） */
export function fetchApproverOptions(keyword?: string) {
  return http.get<any, Array<{ username: string; name?: string }>>(
    '/workorders/approver-options',
    { params: keyword ? { keyword } : {} },
  )
}

/* ---------------- 工单附件 ---------------- */

/** 附件元数据（storageKey 不下发 API） */
export interface WoAttachment {
  id: number
  originalName: string
  contentType?: string
  sizeBytes: number
  uploadedBy?: string
  createdAt?: string
}

/** 附件列表（仅元数据） */
export function fetchWorkorderAttachments(woNo: string) {
  return http.get<any, WoAttachment[]>(`/workorders/${encodeURIComponent(woNo)}/attachments`)
}

/** 上传附件（multipart；白名单 jpg/png/pdf/zip/office/txt 等，单文件 50MB） */
export function uploadWorkorderAttachment(woNo: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<any, WoAttachment>(
    `/workorders/${encodeURIComponent(woNo)}/attachments`,
    form,
    { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 },
  )
}

/** 删除附件（仅上传者本人或管理员；后端二次校验） */
export function deleteWorkorderAttachment(woNo: string, id: number) {
  return http.delete<any, void>(
    `/workorders/${encodeURIComponent(woNo)}/attachments/${id}`,
  )
}

/** 下载附件为 Blob（保存/预览共用） */
export async function downloadWorkorderAttachment(woNo: string, id: number) {
  const res = await http.get<Blob>(
    `/workorders/${encodeURIComponent(woNo)}/attachments/${id}/download`,
    { responseType: 'blob', timeout: 120000 },
  )
  return res.data
}

/** 新增工单资产行（未终态） */
export function addWorkorderItem(woNo: string, payload: WoAssetLineInput) {
  return http.post<any, WoAssetItem>(`/workorders/${encodeURIComponent(woNo)}/items`, payload)
}

/** 修改工单行目标位置（未终态） */
export function updateWorkorderItem(woNo: string, itemNo: string, payload: WoAssetLineInput) {
  return http.put<any, WoAssetItem>(
    `/workorders/${encodeURIComponent(woNo)}/items/${encodeURIComponent(itemNo)}`,
    payload,
  )
}

/** 删除工单资产行（未终态） */
export function deleteWorkorderItem(woNo: string, itemNo: string) {
  return http.delete<any, void>(
    `/workorders/${encodeURIComponent(woNo)}/items/${encodeURIComponent(itemNo)}`,
  )
}

/** 整体覆盖维护子件更换明细（仅故障维修/部件更换、未终态） */
export function updateWorkorderReplacements(woNo: string, replacements: WoReplacement[]) {
  return http.put<any, WoReplacement[]>(
    `/workorders/${encodeURIComponent(woNo)}/replacements`,
    { replacements },
  )
}

// v3.5：通知 API 已迁出至 @/api/notification（通知域独立，SSE/通知中心复用）
