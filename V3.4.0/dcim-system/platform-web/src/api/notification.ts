import http from './client'
import type { PageResult } from './types'

/**
 * 通知域 API（v3.5）：从 api/workorder.ts 迁出独立成域。
 * 后端 NotificationController：/api/notifications/*（登录即可访问，无模块权限门槛）。
 */

/** 通知条目（与后端 NotificationController /my 行结构、SSE notification 帧载荷一致） */
export interface NotificationItem {
  id: number | string
  username?: string
  type?: string
  title: string
  content?: string
  refId?: string
  readFlag?: boolean
  createdAt?: string
}

/** 通知列表查询参数 */
export interface NotificationQuery {
  page: number
  size: number
  unread?: boolean
}

export function fetchMyNotifications(params: NotificationQuery) {
  return http.get<any, PageResult<NotificationItem>>('/notifications/my', { params })
}

export function fetchUnreadCount() {
  // silent：降级轮询每 30s 一次，后端不可用时不弹全局错误（红点数据自行兜底为 0）
  return http.get<any, { count: number }>('/notifications/unread-count', { silent: true })
}

export function markNotificationRead(id: number | string) {
  return http.put<any, void>(`/notifications/${encodeURIComponent(String(id))}/read`)
}

export function markAllNotificationsRead() {
  return http.put<any, void>('/notifications/read-all')
}
