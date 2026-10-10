import http from './client'

export interface ApiToken {
  id: number
  name: string
  tokenPrefix: string
  username: string
  readOnly: boolean
  expiresAt?: string
  lastUsedAt?: string
  lastUsedIp?: string
  createdAt: string
  revokedAt?: string
  state: 'ACTIVE' | 'EXPIRED' | 'REVOKED'
}
export const fetchTokens = (all = false) => http.get<any, ApiToken[]>('/tokens', { params: { all } })
export const createToken = (p: { name: string; readOnly: boolean; expireDays?: number }) =>
  http.post<any, { id: number; token: string; expiresAt?: string }>('/tokens', p)
export const revokeToken = (id: number) => http.delete(`/tokens/${id}`)

export const fetchLdap = () => http.get<any, Record<string, any>>('/system/ldap')
export const testLdap = (username: string, password?: string) =>
  http.post<any, Record<string, any>>('/system/ldap/test', { username, password })

export interface QualityRule {
  id: string
  name: string
  dimension: string
  description: string
  weight: number
  scope: number
  failed: number
  passRate: number
}
export interface QualitySummary {
  total: number
  score: number
  dimensions: Record<string, number>
  rules: QualityRule[]
  byDepartment: { department: string; total: number; problems: number }[]
}
export const fetchQuality = () => http.get<any, QualitySummary>('/data-quality')
export const fetchQualityAssets = (rule: string, page: number, size: number) =>
  http.get<any, { rule: { id: string; name: string; description: string }; total: number; records: any[] }>(
    `/data-quality/rules/${rule}/assets`,
    { params: { page, size } },
  )
