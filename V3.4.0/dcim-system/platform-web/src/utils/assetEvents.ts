/** 资产生命周期事件类型 → 中文 / 标签色（台账详情、生命周期页共用） */
export const ASSET_EVENT_LABEL: Record<string, string> = {
  CREATE: '新增入账',
  IMPORT: '批量导入',
  UPDATE: '信息修改',
  STATUS_CHANGE: '状态流转',
  RACKED: '上架',
  UNRACKED: '下架',
  MOVED: '迁移',
  TRANSFER: '调拨',
  REPLACE_IN: '部件换入',
  REPLACE_OUT: '部件换出',
  GPU_SWAP: 'GPU 更换',
  SNAPSHOT: '带外快照',
  DELETE: '删除',
  // v3.6 时间线聚合新增（工单 / 财务 / 告警来源）
  WO_SUBMIT: '工单发起',
  WO_FINISH: '工单完成',
  DEPRECIATION: '折旧计提',
  ALERT_FIRING: '告警触发',
  ALERT_RESOLVED: '告警恢复',
}

export const ASSET_EVENT_TAG: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  CREATE: 'success',
  IMPORT: 'success',
  UPDATE: 'info',
  STATUS_CHANGE: 'primary',
  RACKED: 'success',
  UNRACKED: 'warning',
  MOVED: 'primary',
  TRANSFER: 'primary',
  REPLACE_IN: 'warning',
  REPLACE_OUT: 'warning',
  GPU_SWAP: 'warning',
  SNAPSHOT: 'info',
  DELETE: 'danger',
  WO_SUBMIT: 'primary',
  WO_FINISH: 'success',
  DEPRECIATION: 'info',
  ALERT_FIRING: 'danger',
  ALERT_RESOLVED: 'success',
}

/** v3.6：时间线来源 → 中文 / 标签色 */
export const TIMELINE_SOURCE_LABEL: Record<string, string> = {
  asset: '台账',
  workorder: '工单',
  fin: '财务',
  alert: '告警',
}

export const TIMELINE_SOURCE_TAG: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  asset: 'info',
  workorder: 'primary',
  fin: 'warning',
  alert: 'danger',
}

export function assetEventLabel(t?: string): string {
  return (t && ASSET_EVENT_LABEL[t]) || t || '-'
}
