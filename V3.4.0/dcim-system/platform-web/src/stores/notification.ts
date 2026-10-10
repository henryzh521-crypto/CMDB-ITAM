import { defineStore } from 'pinia'
import { fetchUnreadCount, markAllNotificationsRead, markNotificationRead } from '@/api/notification'
import type { NotificationItem } from '@/api/notification'

/**
 * 通知实时推送 store（v3.5）。
 *
 * 通道优先级：SSE（/api/notifications/sse，同源 cookie 自动认证，EventSource
 * 无需显式带 token）→ 失败指数退避重连（1s/2s/4s/8s/16s）→ 连续 5 次失败
 * 降级为 30s 轮询未读数（保留 v3.4 行为，功能不中断）。
 * MainLayout 挂载时 start()，卸载/登出时 stop()。
 *
 * SSE 两类帧（均为 event:message，服务端心跳是 :ping 注释帧不触发 onmessage）：
 * - kind=notification：按用户推送的站内信（字段与 /notifications/my 行一致）
 *   → 未读红点 +1，并通知订阅者（MainLayout toast / 通知中心列表刷新）
 * - kind=event：频道广播（workorder/stock/... 有变化的轻量信号）
 *   → 仅通知订阅者，由各列表页自行决定是否刷新
 */

/** SSE 频道事件帧（kind=event） */
export interface ChannelEvent {
  channel: string
  type: string
  objType?: string
  objId?: string
  occurredAt?: string
  data?: Record<string, unknown>
}

const MAX_RETRIES = 5
const BACKOFF_CAP_MS = 30_000
const POLL_MS = 30_000
const SSE_URL = '/api/notifications/sse'

// 非序列化资源（连接/定时器/订阅者）放模块级：不进响应式 state，避免 Proxy 包装与快照开销
let es: EventSource | null = null
let retries = 0
let retryTimer: number | undefined
let pollTimer: number | undefined
const notifCallbacks = new Set<(n: NotificationItem) => void>()
const channelCallbacks = new Set<(e: ChannelEvent) => void>()

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    unreadCount: 0,
    /** 推送通道：connecting=建连/退避中；sse=实时连接；polling=降级轮询；idle=未启动 */
    transport: 'idle' as 'idle' | 'connecting' | 'sse' | 'polling',
  }),

  actions: {
    /** MainLayout 挂载时调用：拉一次未读数并建立 SSE（幂等，重复调用无副作用） */
    start() {
      if (es !== null || retryTimer !== undefined || pollTimer !== undefined) return
      void this.refreshUnread()
      this.connectSse()
    },

    /** MainLayout 卸载/登出时调用：断 SSE、清定时器，回到 idle */
    stop() {
      if (retryTimer !== undefined) {
        window.clearTimeout(retryTimer)
        retryTimer = undefined
      }
      this.stopPolling()
      es?.close()
      es = null
      retries = 0
      this.transport = 'idle'
    },

    /** 拉取服务端未读数（对齐真值；silent 失败不打扰用户） */
    async refreshUnread() {
      try {
        const res = await fetchUnreadCount()
        this.unreadCount = res?.count ?? 0
      } catch {
        // 静默：降级轮询期间后端不可用属预期场景
      }
    },

    /** 标记单条已读：成功后本地红点减一（新到达的未读由 SSE 增量补偿，无需回拉） */
    async markRead(id: number | string) {
      await markNotificationRead(id)
      this.unreadCount = Math.max(0, this.unreadCount - 1)
    },

    /** 全部已读：成功后红点清零 */
    async markAllRead() {
      await markAllNotificationsRead()
      this.unreadCount = 0
    },

    /** 订阅实时到达的站内信（供 toast / 通知中心列表刷新）。返回取消订阅函数。 */
    onNotification(cb: (n: NotificationItem) => void): () => void {
      notifCallbacks.add(cb)
      return () => notifCallbacks.delete(cb)
    },

    /** 订阅频道变化事件（供列表页局部刷新）。返回取消订阅函数。 */
    onChannelEvent(cb: (e: ChannelEvent) => void): () => void {
      channelCallbacks.add(cb)
      return () => channelCallbacks.delete(cb)
    },

    /* ---------------- 内部：SSE 建连 / 退避 / 降级 ---------------- */

    connectSse() {
      this.transport = 'connecting'
      try {
        es = new EventSource(SSE_URL)
      } catch {
        this.degradeToPolling()
        return
      }
      es.onopen = () => {
        retries = 0
        this.transport = 'sse'
        this.stopPolling()
        // 重连窗口内可能漏推，连上后对齐一次服务端真值
        void this.refreshUnread()
      }
      es.onmessage = (ev: MessageEvent<string>) => this.handleFrame(ev.data)
      es.onerror = () => {
        // 完全自管重连：先关掉浏览器默认自动重连，再按自己的退避节奏连，行为可预测
        es?.close()
        es = null
        this.scheduleReconnect()
      }
    },

    handleFrame(raw: string) {
      let frame: Record<string, unknown>
      try {
        frame = JSON.parse(raw) as Record<string, unknown>
      } catch {
        return
      }
      if (frame.kind === 'notification') {
        const n = frame as unknown as NotificationItem
        if (!n.readFlag) this.unreadCount++
        for (const cb of notifCallbacks) cb(n)
      } else if (frame.kind === 'event') {
        const e = frame as unknown as ChannelEvent
        for (const cb of channelCallbacks) cb(e)
      }
    },

    scheduleReconnect() {
      retries++
      if (retries > MAX_RETRIES) {
        this.degradeToPolling()
        return
      }
      const delay = Math.min(1000 * 2 ** (retries - 1), BACKOFF_CAP_MS)
      this.transport = 'connecting'
      retryTimer = window.setTimeout(() => {
        retryTimer = undefined
        this.connectSse()
      }, delay)
    },

    degradeToPolling() {
      this.stopPolling()
      this.transport = 'polling'
      void this.refreshUnread()
      pollTimer = window.setInterval(() => void this.refreshUnread(), POLL_MS)
    },

    stopPolling() {
      if (pollTimer !== undefined) {
        window.clearInterval(pollTimer)
        pollTimer = undefined
      }
    },
  },
})
