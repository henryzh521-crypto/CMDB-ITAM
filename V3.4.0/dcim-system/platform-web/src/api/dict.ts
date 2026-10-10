import http from '@/api/client'

/** 字典项（GET /dicts 行；systemManaged=状态机系统状态，前端据此锁删除） */
export interface DictItem {
  id: number
  dictType: string
  dictKey: string
  dictValue: string
  sort: number
  status: number
  systemManaged: boolean
}

/** 表单项下拉选项（GET /dicts/options 行：仅启用项，只有键与显示名） */
export interface DictOption {
  dictKey: string
  dictValue: string
}

/** 字典项请求体（PUT 必须回传 dictType/dictKey/dictValue，类型与键不可改） */
export interface DictPayload {
  dictType: string
  dictKey: string
  dictValue: string
  sort?: number
  status?: number
}

/** 全量列表（不传 dictType 返回全部；按类型、sort 排序） */
export function fetchDicts(dictType?: string) {
  return http.get<any, DictItem[]>('/dicts', { params: dictType ? { dictType } : undefined })
}

/** 表单下拉用的启用项（只回 dictKey/dictValue；仅需登录，供业务表单选择） */
export function fetchDictOptions(dictType: string) {
  return http.get<any, DictOption[]>('/dicts/options', { params: { dictType } })
}

export function createDict(payload: DictPayload) {
  return http.post<any, void>('/dicts', payload)
}

export function updateDict(id: number, payload: DictPayload) {
  return http.put<any, void>(`/dicts/${id}`, payload)
}

export function deleteDict(id: number) {
  return http.delete<any, void>(`/dicts/${id}`)
}
