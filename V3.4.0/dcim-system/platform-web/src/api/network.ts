import http from './client'

export interface Prefix {
  id: number
  prefix: string
  name: string
  purpose: string
  roomCode?: string
  gateway?: string
  dns?: string
  remark?: string
  vlanRef?: number | null
  vlanId?: number
  vlanName?: string
  family: number
  masklen: number
  parent?: string
  used: number
  reserved: number
  size: number | null
  usedPct: number | null
}

export interface PrefixPayload {
  prefix: string
  name: string
  purpose: string
  vlanRef?: number | null
  roomCode?: string
  gateway?: string
  dns?: string
  remark?: string
}

export interface IpAddress {
  id: number
  ip: string
  status: 'USED' | 'RESERVED' | 'DHCP'
  assetNo?: string
  assetName?: string
  rackCode?: string
  ipRole?: string
  hostname?: string
  source: 'ASSET' | 'MANUAL'
  remark?: string
}

export interface AddressPayload {
  ip?: string
  prefixId?: number
  status?: string
  assetNo?: string
  ipRole?: string
  hostname?: string
  remark?: string
}

export interface Vlan {
  id: number
  vlanId: number
  name: string
  roomCode?: string
  purpose?: string
  remark?: string
  prefixes?: string
}

export interface Cable {
  id: number
  cableNo: string
  cableType: string
  aAssetNo: string
  aAssetName?: string
  aRack?: string
  aUnit?: number
  aPort: string
  bAssetNo: string
  bAssetName?: string
  bRack?: string
  bUnit?: number
  bPort: string
  lengthM?: number
  color?: string
  speed?: string
  status: 'PLANNED' | 'CONNECTED' | 'REMOVED'
  remark?: string
  createdBy?: string
  updatedAt?: string
}

export type CablePayload = Omit<Cable, 'id' | 'aAssetName' | 'bAssetName' | 'aRack' | 'bRack' | 'aUnit' | 'bUnit' | 'createdBy' | 'updatedAt'>

export const PURPOSE_LABEL: Record<string, string> = {
  BIZ: '业务',
  MGMT: '管理',
  BMC: '带外',
  STORAGE: '存储',
  IB: 'IB/RDMA',
  OTHER: '其他',
}
export const ADDR_STATUS_LABEL: Record<string, string> = { USED: '已用', RESERVED: '保留', DHCP: 'DHCP' }
export const CABLE_STATUS_LABEL: Record<string, string> = { PLANNED: '规划', CONNECTED: '已连接', REMOVED: '已拆除' }

export const fetchPrefixes = (params: { keyword?: string; roomCode?: string; purpose?: string }) =>
  http.get<any, Prefix[]>('/network/prefixes', { params })
export const createPrefix = (p: PrefixPayload) => http.post<any, { id: number }>('/network/prefixes', p)
export const updatePrefix = (id: number, p: PrefixPayload) => http.put<any, { id: number }>(`/network/prefixes/${id}`, p)
export const deletePrefix = (id: number) => http.delete(`/network/prefixes/${id}`)
export const fetchPrefixAddresses = (id: number, params: { status?: string; page: number; size: number }) =>
  http.get<any, { total: number; records: IpAddress[] }>(`/network/prefixes/${id}/addresses`, { params })
export const fetchNextFree = (id: number, count = 1) =>
  http.get<any, string[]>(`/network/prefixes/${id}/next-free`, { params: { count } })

export const allocateAddress = (p: AddressPayload) =>
  http.post<any, { ip: string; status: string; assetNo?: string }>('/network/addresses', p)
export const updateAddress = (id: number, p: AddressPayload) => http.put(`/network/addresses/${id}`, p)
export const releaseAddress = (id: number, clearAsset = false) =>
  http.delete(`/network/addresses/${id}`, { params: { clearAsset } })
export const lookupIp = (ip: string) => http.get<any, any>('/network/lookup', { params: { ip } })
export const fetchConflicts = () => http.get<any, any>('/network/conflicts')
export const syncIpam = () => http.post<any, { inserted: number; updated: number; deleted: number }>('/network/sync')

export const fetchVlans = (roomCode?: string) => http.get<any, Vlan[]>('/network/vlans', { params: { roomCode } })
export const createVlan = (p: Omit<Vlan, 'id' | 'prefixes'>) => http.post('/network/vlans', p)
export const updateVlan = (id: number, p: Omit<Vlan, 'id' | 'prefixes'>) => http.put(`/network/vlans/${id}`, p)
export const deleteVlan = (id: number) => http.delete(`/network/vlans/${id}`)

export const fetchCableMeta = () =>
  http.get<any, { types: Record<string, string>; statuses: Record<string, string> }>('/network/cables/meta')
export const fetchCables = (params: {
  assetNo?: string
  rackCode?: string
  type?: string
  status?: string
  keyword?: string
  page: number
  size: number
}) => http.get<any, { total: number; records: Cable[] }>('/network/cables', { params })
export const fetchAssetPorts = (assetNo: string) =>
  http.get<any, any[]>(`/network/cables/ports/${encodeURIComponent(assetNo)}`)
export const createCable = (p: CablePayload) => http.post<any, Cable>('/network/cables', p)
export const updateCable = (id: number, p: CablePayload) => http.put<any, Cable>(`/network/cables/${id}`, p)
export const removeCable = (id: number) => http.post(`/network/cables/${id}/remove`)
export const deleteCable = (id: number) => http.delete(`/network/cables/${id}`)
