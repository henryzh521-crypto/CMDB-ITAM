import type { WorkorderItem } from '@/api/workorder'

/** Element Plus 标签色类型（状态/优先级/范围/动作按钮共用） */
export type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

export const STATUS_TAG: Record<string, TagType> = {
  待处理: 'info',
  待审批: 'warning',
  处理中: 'primary',
  已完成: 'success',
  已驳回: 'danger',
  已关闭: 'info',
}

export const PRIORITY_TAG: Record<string, TagType> = {
  高: 'danger',
  中: 'warning',
  低: 'info',
}

/** 调拨范围徽标色 */
export const SCOPE_TAG: Record<string, TagType> = {
  同机房: 'info',
  跨机房: 'primary',
  跨数据中心: 'warning',
  跨地域: 'danger',
}

export function statusTag(status?: string): TagType {
  return (status && STATUS_TAG[status]) || 'info'
}

export function priorityTag(priority?: string): TagType {
  return (priority && PRIORITY_TAG[priority]) || 'info'
}

export function scopeTag(scope?: string | null): TagType {
  return (scope && SCOPE_TAG[scope]) || 'info'
}

/** 动作按钮色：审批通过绿 / 驳回红 / 其余主色；危险动作给红边提示 */
export function actionBtnType(action: string): TagType {
  if (action === '审批通过') return 'success'
  if (action === '驳回') return 'danger'
  return 'primary'
}

/**
 * SLA 解决截止是否已超期。
 *
 * 优先读后端下发的 `slaOverdue`（P0 起由 `SlaPolicy` 统一计算），
 * 仅在接口未返回该字段时回退到本地按时间比较的旧逻辑 —— 保证
 * 列表标红、筛选与报表用的是同一个口径。
 */
export function isSlaOverdue(
  row: Pick<WorkorderItem, 'slaOverdue' | 'slaResolveDeadline' | 'status'>,
): boolean {
  if (typeof row.slaOverdue === 'boolean') return row.slaOverdue
  if (!row.slaResolveDeadline) return false
  if (row.status === '已完成' || row.status === '已关闭' || row.status === '已驳回') return false
  return new Date(row.slaResolveDeadline).getTime() < Date.now()
}

/** 逗号分隔字段 → 去空白数组 */
export function splitList(v?: string): string[] {
  return v ? v.split(',').map((s) => s.trim()).filter(Boolean) : []
}

/** 今天 yyyy-MM-dd（申请时间区间默认值） */
export function today(): string {
  const d = new Date()
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}
