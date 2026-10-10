import http from './client'
import type { CsvImportResult } from './types'

/** 资产分类树节点 */
export interface CategoryNode {
  id: number | string
  categoryCode: string
  name: string
  parentCode?: string | null
  level: number
  path?: string
  sort?: number
  status?: string | number
  remark?: string
  children?: CategoryNode[]
}

/** 新增分类请求体（level/path 由后端计算） */
export interface CategoryCreatePayload {
  categoryCode: string
  name: string
  parentCode?: string
  sort?: number
  remark?: string
}

/** 编辑分类请求体（仅 name/sort/status/remark） */
export interface CategoryUpdatePayload {
  name?: string
  sort?: number
  status?: number
  remark?: string
}

export function fetchCategoryTree() {
  return http.get<any, CategoryNode[]>('/categories/tree')
}

export function createCategory(payload: CategoryCreatePayload) {
  return http.post<any, void>('/categories', payload)
}

export function updateCategory(categoryCode: string, payload: CategoryUpdatePayload) {
  return http.put<any, void>(`/categories/${encodeURIComponent(categoryCode)}`, payload)
}

export function deleteCategory(categoryCode: string) {
  return http.delete<any, void>(`/categories/${encodeURIComponent(categoryCode)}`)
}

/** 分类 CSV 导入模板 */
export async function fetchCategoryImportTemplate() {
  const res = await http.get<Blob>('/categories/template', { responseType: 'blob' })
  return res.data
}

/** 分类 CSV 批量导入 */
export function importCategories(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<FormData, CsvImportResult>('/categories/import', form)
}
