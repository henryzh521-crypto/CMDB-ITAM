import http from './client'
import type { LoginResult } from './types'

/** 企微接入配置（后端未启用时 enabled=false，移动端走开发模式登录） */
export interface WecomConfig {
  enabled: boolean
  corpId: string
  agentId: string
  /**
   * 后端 dev-login 是否可用：需要显式开关 + 未接入企微 + 非 prod 三重条件同时满足。
   * 前端据此决定是否渲染「免密登录」面板——不能只看 enabled=false 就展示，
   * 否则 prod 上没接企微的部署会凭空出现一个免密入口（后端会 429/403 拒绝，但徒增攻击面与困惑）。
   */
  devLoginEnabled?: boolean
}

/** 移动端资产速查条目 */
export interface AssetLookupItem {
  assetNo: string
  sn?: string
  name: string
  statusBiz?: string
  statusFin?: string
  roomCode?: string
  rackCode?: string
  unitNo?: string
  owner?: string
  categoryCode?: string
  modelCode?: string
}

/** 企微登录响应（与账密登录同构，dev-login 额外带 devMode） */
export type WecomLoginResult = LoginResult & { devMode?: boolean }

export function fetchWecomConfig() {
  return http.get<any, WecomConfig>('/auth/wecom/config')
}

/** 企微 OAuth code 登录 */
export function wecomLogin(code: string) {
  return http.post<any, WecomLoginResult>('/auth/wecom/login', { code })
}

/** 开发模式：用户名直登（免密，仅后端未接入企微时开放） */
export function wecomDevLogin(username: string) {
  return http.post<any, WecomLoginResult>('/auth/wecom/dev-login', { username })
}

/** 移动端资产速查（编号 / SN / 名称模糊匹配） */
export function lookupAssets(q: string) {
  return http.get<any, AssetLookupItem[]>('/assets/lookup', { params: { q } })
}
