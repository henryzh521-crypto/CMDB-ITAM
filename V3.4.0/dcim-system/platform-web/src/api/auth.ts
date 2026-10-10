import http from './client'
import type { LoginResult, MeResult } from './types'

export function login(username: string, password: string) {
  return http.post<any, LoginResult>('/auth/login', { username, password })
}

export function refresh() {
  return http.post<any, void>('/auth/refresh')
}

export function logout() {
  return http.post<any, void>('/auth/logout')
}

export function fetchMe() {
  return http.get<any, MeResult>('/auth/me')
}

export function changePassword(username: string, oldPassword: string, newPassword: string) {
  return http.put<any, void>(`/users/${encodeURIComponent(username)}/password`, {
    oldPassword,
    newPassword,
  })
}
