import http from './client'

/* ---------------- 知识库 ---------------- */
export interface KbArticle {
  id: number
  kbNo: string
  title: string
  category: string
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  tags: string[]
  alertnames: string[]
  modelCodes: string[]
  categoryCodes: string[]
  sourceProblem?: string
  views: number
  helpful: number
  unhelpful: number
  createdBy?: string
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
  publishedAt?: string
  content?: string
  excerpt?: string
}
export interface KbPayload {
  title: string
  category: string
  content: string
  status: string
  tags: string[]
  alertnames: string[]
  modelCodes: string[]
  categoryCodes: string[]
}
export interface KbRecommend {
  id: number
  kbNo: string
  title: string
  category: string
  helpful: number
  views: number
  score: number
}

export const KB_CATEGORY_LABEL: Record<string, string> = {
  TROUBLESHOOT: '故障处理',
  SOP: '操作规程',
  KNOWN_ERROR: '已知错误',
  FAQ: '常见问题',
  OTHER: '其他',
}
export const KB_STATUS_LABEL: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }

export const fetchKb = (params: { keyword?: string; category?: string; status?: string; tag?: string; page: number; size: number }) =>
  http.get<any, { total: number; records: KbArticle[] }>('/kb', { params })
export const fetchKbArticle = (idOrNo: string | number) => http.get<any, KbArticle>(`/kb/${idOrNo}`)
export const createKb = (p: KbPayload) => http.post<any, KbArticle>('/kb', p)
export const updateKb = (id: number, p: KbPayload) => http.put<any, KbArticle>(`/kb/${id}`, p)
export const deleteKb = (id: number) => http.delete(`/kb/${id}`)
export const feedbackKb = (id: number, helpful: boolean) =>
  http.post<any, { helpful: number; unhelpful: number }>(`/kb/${id}/feedback`, { helpful })
export const recommendKb = (params: { alertname?: string; assetNo?: string; text?: string; limit?: number }) =>
  http.get<any, KbRecommend[]>('/kb/recommend', { params })

/* ---------------- 问题管理 ---------------- */
export interface ProblemItem {
  id: number
  prbNo: string
  title: string
  description?: string
  status: string
  priority: string
  owner?: string
  assetNos: string[]
  modelCode?: string
  alertname?: string
  rootCause?: string
  workaround?: string
  resolution?: string
  kbNo?: string
  source: string
  createdBy?: string
  createdAt: string
  updatedAt: string
  resolvedAt?: string
  closedAt?: string
  woCount?: number
  alertCount?: number
}
export interface ProblemDetail extends ProblemItem {
  links: { id: number; linkType: string; refNo: string; title?: string; status?: string; refTime?: string }[]
  flow: { fromStatus?: string; toStatus: string; actor?: string; comment?: string; createdAt: string }[]
  assets: { assetNo: string; name: string; modelCode?: string; rackCode?: string; statusBiz?: string }[]
  recommend: KbRecommend[]
}
export interface ProblemPayload {
  title: string
  description?: string
  priority?: string
  owner?: string
  assetNos?: string[]
  modelCode?: string
  alertname?: string
  rootCause?: string
  workaround?: string
  source?: string
  links?: { type: string; ref: string }[]
}

export const PROBLEM_STATUS_LABEL: Record<string, string> = {
  NEW: '新建',
  ANALYZING: '分析中',
  KNOWN_ERROR: '已知错误',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
}
export const PROBLEM_STATUS_TAG: Record<string, 'info' | 'warning' | 'danger' | 'success' | 'primary'> = {
  NEW: 'danger',
  ANALYZING: 'warning',
  KNOWN_ERROR: 'primary',
  RESOLVED: 'success',
  CLOSED: 'info',
}
export const PROBLEM_NEXT: Record<string, string[]> = {
  NEW: ['ANALYZING', 'KNOWN_ERROR', 'RESOLVED', 'CLOSED'],
  ANALYZING: ['KNOWN_ERROR', 'RESOLVED', 'CLOSED'],
  KNOWN_ERROR: ['ANALYZING', 'RESOLVED', 'CLOSED'],
  RESOLVED: ['ANALYZING', 'CLOSED'],
  CLOSED: [],
}

export const fetchProblems = (params: { status?: string; keyword?: string; owner?: string; assetNo?: string; page: number; size: number }) =>
  http.get<any, { total: number; records: ProblemItem[] }>('/problems', { params })
export const fetchProblemStats = () =>
  http.get<any, { byStatus: Record<string, number>; resolved30d: number; avgResolveDays: number }>('/problems/stats')
export const fetchProblemSuggestions = () => http.get<any, { repeatFaults: any[]; repeatAlerts: any[]; modelDefects: any[] }>('/problems/suggestions')
export const fetchProblem = (no: string) => http.get<any, ProblemDetail>(`/problems/${no}`)
export const createProblem = (p: ProblemPayload) => http.post<any, ProblemDetail>('/problems', p)
export const updateProblem = (no: string, p: ProblemPayload) => http.put<any, ProblemDetail>(`/problems/${no}`, p)
export const transitionProblem = (
  no: string,
  p: { to: string; rootCause?: string; workaround?: string; resolution?: string; comment?: string; createKb?: boolean },
) => http.post<any, ProblemDetail>(`/problems/${no}/transition`, p)
export const linkProblem = (no: string, type: string, ref: string) => http.post<any, ProblemDetail>(`/problems/${no}/links`, { type, ref })
export const unlinkProblem = (no: string, linkId: number) => http.delete(`/problems/${no}/links/${linkId}`)
export const deleteProblem = (no: string) => http.delete(`/problems/${no}`)
/** 反查：某工单 / 告警 / 变更关联了哪些问题单 */
export const fetchProblemsByRef = (type: 'WORKORDER' | 'ALERT' | 'CHANGE', ref: string) =>
  http.get<any, { prbNo: string; title: string; status: string }[]>('/problems/by-ref', { params: { type, ref }, silent: true })

/* ---------------- 服务目录 ---------------- */
export interface FormField {
  key: string
  label: string
  type: 'text' | 'textarea' | 'number' | 'select' | 'date' | 'asset' | 'assets' | 'rack'
  required?: boolean
  options?: string[]
  placeholder?: string
  maxLength?: number
}
export interface CatalogItem {
  id: number
  code: string
  name: string
  category: string
  description?: string
  icon?: string
  woType: string
  priority: string
  formSchema: FormField[]
  visibleRoles: string[]
  slaHint?: string
  enabled: boolean
  sort: number
}
export type CatalogPayload = Omit<CatalogItem, 'id'>

export const fetchCatalog = (all = false) => http.get<any, CatalogItem[]>('/catalog/items', { params: { all } })
export const submitCatalogRequest = (id: number, values: Record<string, unknown>) =>
  http.post<any, { woNo: string; status: string }>(`/catalog/items/${id}/request`, values)
export const fetchMyRequests = (page: number, size: number) =>
  http.get<any, { total: number; records: any[] }>('/catalog/my-requests', { params: { page, size } })
export const fetchCatalogWoTypes = () => http.get<any, string[]>('/catalog/wo-types')
export const createCatalogItem = (p: CatalogPayload) => http.post<any, { id: number }>('/catalog/items', p)
export const updateCatalogItem = (id: number, p: CatalogPayload) => http.put<any, { id: number }>(`/catalog/items/${id}`, p)
export const deleteCatalogItem = (id: number) => http.delete(`/catalog/items/${id}`)
