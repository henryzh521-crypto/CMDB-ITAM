import http from './client'
import type { PageResult } from './types'

export interface AlertItem {
  id: number
  fingerprint: string
  source: string
  alertname: string
  severity: 'critical' | 'warning' | 'info' | string
  status: 'FIRING' | 'RESOLVED'
  summary?: string
  description?: string
  labels?: Record<string, string>
  instance?: string
  assetNo?: string
  rackCode?: string
  roomCode?: string
  startsAt: string
  endsAt?: string
  lastReceivedAt?: string
  fireCount?: number
  suppressed: boolean
  suppressReason?: string
  ackBy?: string
  ackAt?: string
  ackNote?: string
  woNo?: string
  generatorUrl?: string
  closeReason?: string
}

export interface AlertStats {
  firingBySeverity: Record<string, number>
  suppressed: number
  unacked: number
  new24h: number
  mttrMinutes: number
  topAssets: { assetNo: string; assetName?: string; n: number }[]
  topAlerts: { alertname: string; n: number }[]
  daily: { day: string; severity: string; n: number }[]
}

export interface AlertQuery {
  page: number
  size: number
  status?: string
  severity?: string
  keyword?: string
  assetNo?: string
  suppressed?: boolean
  acked?: boolean
}

export const fetchAlerts = (params: AlertQuery) => http.get<any, PageResult<AlertItem>>('/alerts', { params })
export const fetchAlertStats = () => http.get<any, AlertStats>('/alerts/stats')
export const fetchAlertDetail = (id: number) =>
  http.get<any, { alert: AlertItem; history: any[]; related?: any[] }>(`/alerts/${id}`)
export const ackAlert = (id: number, note?: string) => http.post<any, AlertItem>(`/alerts/${id}/ack`, { note })
export const ackAlerts = (ids: number[], note?: string) => http.post<any, { acked: number }>('/alerts/ack-batch', { ids, note })
export const ticketAlert = (id: number) => http.post<any, { woNo: string }>(`/alerts/${id}/ticket`)

export interface JobHealth {
  cmdbTargets?: number
  up?: number
  down?: number
  total?: number
  errors?: { instance: string; assetNo?: string; error: string }[]
}

export interface MonitoringOverview {
  prometheusConfigured: boolean
  prometheusUp?: boolean
  prometheusError?: string
  jobs: Record<string, JobHealth>
  monitoredAssets: number
}

export const fetchMonitoringOverview = () => http.get<any, MonitoringOverview>('/monitoring/overview')

export interface MetricSeries {
  metric: Record<string, string>
  points: [number, number | null][]
}

export interface AssetMetrics {
  assetNo: string
  hours: number
  series: Record<string, MetricSeries[]>
  current: Record<string, unknown>
  up: { metric: Record<string, string>; value: number }[]
}

export const fetchAssetMetrics = (assetNo: string, hours: number) =>
  http.get<any, AssetMetrics>(`/monitoring/assets/${encodeURIComponent(assetNo)}/metrics`, { params: { hours } })

export interface AlertRule {
  group: string
  name: string
  query: string
  duration: number
  severity?: string
  summary?: string
  state: string
  firing: number
  health: string
}
export const fetchAlertRules = () => http.get<any, AlertRule[]>('/monitoring/rules')

export interface MonitorProfile {
  id: number
  job: string
  categoryPattern: string
  ipField: 'biz_ip' | 'mgmt_ip'
  port?: number | null
  enabled: boolean
  remark?: string
}
export type ProfilePayload = Omit<MonitorProfile, 'id'>
export const fetchProfiles = () => http.get<any, MonitorProfile[]>('/monitoring/profiles')
export const createProfile = (p: ProfilePayload) => http.post<any, MonitorProfile>('/monitoring/profiles', p)
export const updateProfile = (id: number, p: ProfilePayload) => http.put<any, MonitorProfile>(`/monitoring/profiles/${id}`, p)
export const deleteProfile = (id: number) => http.delete<any, void>(`/monitoring/profiles/${id}`)
export const previewProfile = (job: string) =>
  http.get<any, { total: number; targets: { targets: string[]; labels: Record<string, string> }[] }>('/monitoring/profiles/preview', {
    params: { job },
  })

export interface MonitoringSettingsResp {
  values: { key: string; value: string; remark?: string }[]
  tokenConfigured: boolean
  effectivePrometheusUrl?: string
  sdJobs: string[]
}
export const fetchMonitoringSettings = () => http.get<any, MonitoringSettingsResp>('/monitoring/settings')
export const saveMonitoringSettings = (values: Record<string, string>) => http.put<any, void>('/monitoring/settings', values)

export interface MaintenanceWindowItem {
  id: number
  name: string
  startsAt: string
  endsAt: string
  assetNos: string[]
  rackCodes: string[]
  roomCodes: string[]
  alertnames: string[]
  reason?: string
  createdBy?: string
  state: 'PLANNED' | 'ACTIVE' | 'ENDED'
}
export interface WindowPayload {
  name: string
  startsAt: string
  endsAt: string
  assetNos: string[]
  rackCodes: string[]
  roomCodes: string[]
  alertnames: string[]
  reason?: string
  global?: boolean
}
export const fetchWindows = (params: { page: number; size: number; activeOnly?: boolean }) =>
  http.get<any, PageResult<MaintenanceWindowItem>>('/maintenance-windows', { params })
export const fetchMaintenanceChanges = () => http.get<any, any[]>('/maintenance-windows/changes')
export const createWindow = (p: WindowPayload) => http.post<any, any>('/maintenance-windows', p)
export const updateWindow = (id: number, p: WindowPayload) => http.put<any, any>(`/maintenance-windows/${id}`, p)
export const endWindow = (id: number) => http.post<any, any>(`/maintenance-windows/${id}/end`)
export const deleteWindow = (id: number) => http.delete<any, void>(`/maintenance-windows/${id}`)

export const SEVERITY_LABEL: Record<string, string> = { critical: '严重', warning: '警告', info: '提示' }
export const SEVERITY_TAG: Record<string, 'danger' | 'warning' | 'info'> = { critical: 'danger', warning: 'warning', info: 'info' }
export const JOB_LABEL: Record<string, string> = {
  icmp: 'Ping 存活',
  node: '操作系统',
  dcgm: 'GPU',
  ipmi: '带外硬件',
  snmp: '网络/设施 SNMP',
  platform: '平台自身',
  prometheus: 'Prometheus',
  alertmanager: 'Alertmanager',
  'blackbox-exporter': 'Blackbox',
}
