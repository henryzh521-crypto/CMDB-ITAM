import http from './client'
import type { PageResult } from './types'

export interface WebhookEndpointItem {
  id: number
  name: string
  url: string
  format: string
  eventTypes: string[]
  enabled: boolean
  hasSecret: boolean
  lastStatus?: string
  lastDeliveryAt?: string
  consecutiveFailures?: number
  createdBy?: string
  createdAt?: string
}

export interface WebhookDeliveryItem {
  id: number
  eventId: string
  eventType: string
  status: 'PENDING' | 'SUCCESS' | 'RETRY' | 'DEAD'
  attempts: number
  responseCode?: number
  responseSnippet?: string
  lastError?: string
  durationMs?: number
  nextAttemptAt?: string
  deliveredAt?: string
  createdAt?: string
  payload?: Record<string, unknown>
}

export interface WebhookMeta {
  eventTypes: { value: string; label: string }[]
  formats: { value: string; label: string }[]
}

export interface WebhookPayload {
  name?: string
  url?: string
  format?: string
  secret?: string
  clearSecret?: boolean
  eventTypes?: string[]
  enabled?: boolean
}

export function fetchWebhookMeta() {
  return http.get<any, WebhookMeta>('/webhooks/meta')
}

export function fetchWebhookEndpoints(params: { page: number; size: number }) {
  return http.get<any, PageResult<WebhookEndpointItem>>('/webhooks', { params })
}

export function createWebhookEndpoint(payload: WebhookPayload) {
  return http.post<any, WebhookEndpointItem>('/webhooks', payload)
}

export function updateWebhookEndpoint(id: number, payload: WebhookPayload) {
  return http.put<any, WebhookEndpointItem>(`/webhooks/${id}`, payload)
}

export function deleteWebhookEndpoint(id: number) {
  return http.delete<any, void>(`/webhooks/${id}`)
}

export function testWebhookEndpoint(id: number) {
  return http.post<any, WebhookDeliveryItem>(`/webhooks/${id}/test`)
}

export function fetchWebhookDeliveries(id: number, params: { page: number; size: number; status?: string }) {
  return http.get<any, PageResult<WebhookDeliveryItem>>(`/webhooks/${id}/deliveries`, { params })
}

export function redeliverWebhook(deliveryId: number) {
  return http.post<any, void>(`/webhooks/deliveries/${deliveryId}/redeliver`)
}
