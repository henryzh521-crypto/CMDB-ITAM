import http from './client'
import type { PageResult } from './types'

/* ---------------- 供应商 ---------------- */

/** 供应商条目 */
export interface SupplierItem {
  supplierCode: string
  name: string
  type?: string
  contact?: string
  phone?: string
  email?: string
  hotline?: string
  products?: string
  address?: string
  status?: string
  remark?: string
  createdAt?: string
}

export interface SupplierQuery {
  page: number
  size: number
  keyword?: string
  status?: string
  type?: string
}

/** 新增 / 编辑供应商公共字段（supplierCode 仅创建时传） */
export interface SupplierPayload {
  name: string
  type?: string
  contact?: string
  phone?: string
  email?: string
  hotline?: string
  products?: string
  address?: string
  status?: string
  remark?: string
}

export interface SupplierCreatePayload extends SupplierPayload {
  supplierCode: string
}

export function fetchSuppliers(params: SupplierQuery) {
  return http.get<any, PageResult<SupplierItem>>('/suppliers', { params })
}

export function fetchSupplier(supplierCode: string) {
  return http.get<any, SupplierItem>(`/suppliers/${encodeURIComponent(supplierCode)}`)
}

export function createSupplier(payload: SupplierCreatePayload) {
  return http.post<any, void>('/suppliers', payload)
}

export function updateSupplier(supplierCode: string, payload: Partial<SupplierPayload>) {
  return http.put<any, void>(`/suppliers/${encodeURIComponent(supplierCode)}`, payload)
}

export function deleteSupplier(supplierCode: string) {
  return http.delete<any, void>(`/suppliers/${encodeURIComponent(supplierCode)}`)
}

/* ---------------- 合同 ---------------- */

/** 合同条目 */
export interface ContractItem {
  contractNo: string
  name: string
  type: string
  party?: string
  amount?: number
  signDate?: string
  startDate?: string
  endDate?: string
  discountRate?: number
  purchaseOption?: boolean
  paymentPlan?: Array<Record<string, unknown>>
  status?: string
  remark?: string
  createdAt?: string
}

export interface ContractQuery {
  page: number
  size: number
  keyword?: string
  status?: string
  type?: string
  endBefore?: string
}

/** 新增 / 编辑合同公共字段（contractNo 仅创建时传） */
export interface ContractPayload {
  name: string
  type: string
  party?: string
  amount?: number
  signDate?: string
  startDate?: string
  endDate?: string
  discountRate?: number
  purchaseOption?: boolean
  paymentPlan?: Array<Record<string, unknown>>
  status?: string
  remark?: string
}

export interface ContractCreatePayload extends ContractPayload {
  contractNo: string
}

export function fetchContracts(params: ContractQuery) {
  return http.get<any, PageResult<ContractItem>>('/contracts', { params })
}

export function fetchContract(contractNo: string) {
  return http.get<any, ContractItem>(`/contracts/${encodeURIComponent(contractNo)}`)
}

export function createContract(payload: ContractCreatePayload) {
  return http.post<any, void>('/contracts', payload)
}

export function updateContract(contractNo: string, payload: Partial<ContractPayload>) {
  return http.put<any, void>(`/contracts/${encodeURIComponent(contractNo)}`, payload)
}

export function deleteContract(contractNo: string) {
  return http.delete<any, void>(`/contracts/${encodeURIComponent(contractNo)}`)
}
