import http from './client'

export interface CiRelation {
  id: number
  direction: 'OUT' | 'IN'
  srcType: string
  srcId: string
  srcName?: string
  relType: string
  relLabel?: string
  dstType: string
  dstId: string
  dstName?: string
  remark?: string
  createdBy?: string
  createdAt?: string
}

export interface CiNode {
  type: string
  id: string
  name: string
  status?: string
  depth: number
}

export interface CiEdge {
  from: string
  to: string
  rel: string
  label: string
  implicit: boolean
}

export interface CiGraph {
  root: CiNode
  nodes: CiNode[]
  edges: CiEdge[]
  truncated: boolean
  summary: Record<string, number>
}

export interface CiMeta {
  relTypes: { value: string; label: string }[]
  ciTypes: { value: string; label: string }[]
}

export function fetchCiMeta() {
  return http.get<any, CiMeta>('/ci-relations/meta')
}

export function fetchCiRelations(srcType: string, srcId: string) {
  return http.get<any, CiRelation[]>('/ci-relations', { params: { srcType, srcId } })
}

export function createCiRelation(payload: {
  srcType: string
  srcId: string
  relType: string
  dstType: string
  dstId: string
  remark?: string
}) {
  return http.post<any, { id: number }>('/ci-relations', payload)
}

export function deleteCiRelation(id: number) {
  return http.delete<any, void>(`/ci-relations/${id}`)
}

export function fetchImpactGraph(type: string, id: string, direction: 'downstream' | 'upstream' = 'downstream', depth = 6) {
  return http.get<any, CiGraph>('/ci-relations/impact', { params: { type, id, direction, depth } })
}

/** v3.5：全量拓扑（网络拓扑页；无根节点，机房/机柜/资产隐含层级 + 显式关系） */
export interface CiFullGraph {
  nodes: CiNode[]
  edges: CiEdge[]
  truncated: boolean
  summary?: Record<string, number>
}

/** roomCode 省略 = 全部机房；服务端按 500 节点截断（truncated 标记） */
export function fetchFullGraph(roomCode?: string) {
  return http.get<any, CiFullGraph>('/ci-relations/graph', {
    params: roomCode ? { roomCode } : undefined,
  })
}

export const CI_TYPE_LABEL: Record<string, string> = { ASSET: '资产', RACK: '机柜', ROOM: '机房' }
