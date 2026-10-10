import http from './client'
import type { PageResult } from './types'

export type ChangeStatus =
  | 'DRAFT'
  | 'PENDING_CAB'
  | 'APPROVED'
  | 'REJECTED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

export interface ChangeRequestItem {
  id: number
  crNo: string
  title: string
  description?: string
  changeType: 'STANDARD' | 'NORMAL' | 'EMERGENCY'
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  impactScope?: string
  rollbackPlan?: string
  status: ChangeStatus
  statusLabel?: string
  requester: string
  cabApproval?: boolean
  cabApprovers?: string[]
  cabComment?: string
  approvedBy?: string
  approvedAt?: string
  scheduledStart?: string
  scheduledEnd?: string
  actualStart?: string
  actualEnd?: string
  closeNote?: string
  cancelReason?: string
  createdAt?: string
  updatedAt?: string
}

export interface ImpactNode {
  type: string
  id: string
  name: string
  status?: string
  depth: number
}

export interface ChangeDetail extends ChangeRequestItem {
  assetNos: string[]
  conflicts: Array<{ id: number; crNo: string; title: string; status: string; scheduledStart: string; scheduledEnd: string }>
  impact: { count: number; nodes: ImpactNode[] }
}

export interface ChangeDraft {
  title: string
  description?: string
  changeType: string
  riskLevel: string
  impactScope?: string
  rollbackPlan?: string
  scheduledStart?: string | null
  scheduledEnd?: string | null
  assetNos?: string[]
}

export function fetchChangeRequests(params: { page: number; size: number; status?: string; keyword?: string; mine?: boolean }) {
  return http.get<any, PageResult<ChangeRequestItem>>('/changes', { params })
}

export function fetchChangeRequest(id: number) {
  return http.get<any, ChangeDetail>(`/changes/${id}`)
}

export function createChangeRequest(payload: ChangeDraft) {
  return http.post<any, ChangeRequestItem>('/changes', payload)
}

export function updateChangeRequest(id: number, payload: ChangeDraft) {
  return http.put<any, ChangeRequestItem>(`/changes/${id}`, payload)
}

export function submitChangeRequest(id: number) {
  return http.post<any, ChangeRequestItem>(`/changes/${id}/submit`)
}

export function cabApproveChangeRequest(id: number, payload: { approved: boolean; comment?: string }) {
  return http.post<any, ChangeRequestItem>(`/changes/${id}/cab-approve`, payload)
}

export function startChangeRequest(id: number) {
  return http.post<any, ChangeRequestItem>(`/changes/${id}/start`)
}

export function completeChangeRequest(id: number, note?: string) {
  return http.post<any, ChangeRequestItem>(`/changes/${id}/complete`, { note })
}

export function failChangeRequest(id: number, note: string) {
  return http.post<any, ChangeRequestItem>(`/changes/${id}/fail`, { note })
}

export function cancelChangeRequest(id: number, note?: string) {
  return http.post<any, ChangeRequestItem>(`/changes/${id}/cancel`, { note })
}

export function deleteChangeRequest(id: number) {
  return http.delete<any, void>(`/changes/${id}`)
}

export const CHANGE_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_CAB: '待CAB审批',
  APPROVED: '已批准',
  REJECTED: '已驳回',
  IN_PROGRESS: '实施中',
  COMPLETED: '已完成',
  FAILED: '失败已回退',
  CANCELLED: '已取消',
}

export const CHANGE_TYPE_LABEL: Record<string, string> = {
  STANDARD: '标准变更',
  NORMAL: '常规变更',
  EMERGENCY: '紧急变更',
}

export const RISK_LABEL: Record<string, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '极高',
}
