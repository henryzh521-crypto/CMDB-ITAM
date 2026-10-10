<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notification'
import { fetchMyNotifications } from '@/api/notification'
import type { NotificationItem } from '@/api/notification'
import type { RouteLocationRaw } from 'vue-router'

/**
 * 通知中心（v3.5）：站内信全量视图——全部/未读筛选、分页、单条/全部已读、
 * 按类型跳转（工单/问题/变更/库存等）；SSE 实时到达的通知在第一页时自动刷新，
 * 推送通道降级为轮询时顶部状态标签提示（不中断使用）。
 */

const PAGE_SIZE = 20

/** 类型元数据：标签 / tag 颜色 / refId 跳转（无 to 则仅标读不跳转） */
interface TypeMeta {
  label: string
  tag: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  to?: (refId: string) => RouteLocationRaw
}

const woDetail = (r: string): RouteLocationRaw => ({ path: `/workorders/${encodeURIComponent(r)}` })

const EXACT_META: Record<string, TypeMeta> = {
  SLA: { label: 'SLA 升级', tag: 'danger', to: woDetail },
  STOCK_LOW: {
    label: '库存预警',
    tag: 'warning',
    // ticket 模式 refId=工单号（跳详情）；notify 模式 refId=物料编码（跳库存水位）
    to: (r) => (r.startsWith('WO-') ? woDetail(r) : { path: '/rfid/stock', query: { keyword: r } }),
  },
  GPU_ALERT: { label: 'GPU 告警', tag: 'danger', to: woDetail },
  INSPECTION: { label: '巡检', tag: 'success', to: woDetail },
  PROBLEM: { label: '问题', tag: 'warning', to: (r) => ({ path: '/problems', query: { id: r } }) },
  CHANGE: { label: '变更', tag: 'info', to: (r) => ({ path: '/changes', query: { id: r } }) },
  CONTRACT: { label: '合同', tag: 'info', to: (r) => ({ path: '/suppliers/contracts', query: { id: r } }) },
}

const DEFAULT_META: TypeMeta = { label: '通知', tag: 'info' }

function metaOf(type?: string): TypeMeta {
  if (!type) return DEFAULT_META
  if (EXACT_META[type]) return EXACT_META[type]
  if (type.startsWith('WO_')) return { label: '工单', tag: 'primary', to: woDetail }
  return DEFAULT_META
}

function fmtTime(s?: string): string {
  return (s || '').replace('T', ' ')
}

const store = useNotificationStore()
const router = useRouter()
const route = useRoute()

const tab = ref<'all' | 'unread'>('all')
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const list = ref<NotificationItem[]>([])

const transportTag = computed(() => {
  switch (store.transport) {
    case 'sse':
      return { text: '实时推送', type: 'success' as const }
    case 'polling':
      return { text: '轮询模式', type: 'info' as const }
    default:
      return { text: '连接中', type: 'warning' as const }
  }
})

/** 列表请求序号：SSE 到达刷新与翻页/切 tab 并发时，仅接受最后一次请求的响应 */
let loadSeq = 0

async function load() {
  const seq = ++loadSeq
  loading.value = true
  try {
    const res = await fetchMyNotifications({
      page: page.value,
      size: PAGE_SIZE,
      unread: tab.value === 'unread' ? true : undefined,
    })
    if (seq !== loadSeq) return
    list.value = res.records ?? []
    total.value = res.total ?? 0
  } catch {
    // 拦截器已提示
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function onTabChange() {
  page.value = 1
  void load()
}

function onPageChange(p: number) {
  page.value = p
  void load()
}

/** 点击通知：未读先标读（红点联动），再按类型跳转；已读直接跳转 */
async function onClick(item: NotificationItem) {
  if (!item.readFlag) {
    try {
      await store.markRead(item.id)
      item.readFlag = true
    } catch {
      return // 标读失败不跳转，拦截器已提示
    }
  }
  const meta = metaOf(item.type)
  if (meta.to && item.refId) router.push(meta.to(item.refId))
}

async function onReadAll() {
  try {
    await store.markAllRead()
    ElMessage.success('已全部标记为已读')
    void load()
  } catch {
    // 拦截器已提示
  }
}

/* SSE 实时到达：处于第一页时自动刷新，让最新通知立即可见 */
let offNotification: (() => void) | undefined

onMounted(() => {
  // 从别处（如铃铛面板）带着 unread 筛选进入
  if (route.query.tab === 'unread') tab.value = 'unread'
  void load()
  offNotification = store.onNotification(() => {
    if (page.value === 1) void load()
  })
})

onUnmounted(() => {
  offNotification?.()
})
</script>

<template>
  <div class="noti-center">
    <div class="noti-toolbar">
      <el-radio-group v-model="tab" @change="onTabChange">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="unread">未读（{{ store.unreadCount }}）</el-radio-button>
      </el-radio-group>
      <div class="noti-toolbar-right">
        <el-tag :type="transportTag.type" size="small" effect="plain" round>
          {{ transportTag.text }}
        </el-tag>
        <el-button :icon="Refresh" @click="load()">刷新</el-button>
        <el-button type="primary" plain :disabled="store.unreadCount <= 0" @click="onReadAll">全部已读</el-button>
      </div>
    </div>

    <div v-loading="loading" class="noti-list">
      <div
        v-for="n in list"
        :key="String(n.id)"
        class="noti-row"
        :class="{ 'is-unread': !n.readFlag }"
        @click="onClick(n)"
      >
        <div class="noti-row-head">
          <span v-if="!n.readFlag" class="noti-dot"></span>
          <el-tag :type="metaOf(n.type).tag" size="small" effect="light">
            {{ metaOf(n.type).label }}
          </el-tag>
          <span class="noti-title">{{ n.title }}</span>
          <span class="noti-time">{{ fmtTime(n.createdAt) }}</span>
        </div>
        <div v-if="n.content" class="noti-content">{{ n.content }}</div>
      </div>
      <el-empty v-if="!loading && list.length === 0" description="暂无通知" />
    </div>

    <div class="noti-pager">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="PAGE_SIZE"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.noti-center {
  max-width: 960px;
  margin: 0 auto;
  background: var(--el-bg-color);
  border-radius: 6px;
  padding: 16px;
}

.noti-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.noti-toolbar-right {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.noti-list {
  min-height: 200px;
}

.noti-row {
  padding: 12px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
}

.noti-row:hover {
  background: var(--el-fill-color-light);
}

.noti-row-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.noti-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--el-color-danger);
  flex-shrink: 0;
}

.noti-title {
  font-size: 14px;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.noti-row.is-unread .noti-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.noti-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  flex-shrink: 0;
}

.noti-content {
  margin: 6px 0 0 15px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.noti-pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
