import http from './client'

export interface SearchResultItem {
  type: string
  id: string
  title: string
  status?: string
}

export function globalSearch(keyword: string, limit = 10) {
  return http.get<any, SearchResultItem[]>('/search', { params: { keyword, limit } })
}
