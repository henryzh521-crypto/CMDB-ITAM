<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { Search, Monitor, Sunny, Moon, User, ArrowDown, Bell } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import http from '@/api/client'
import { useThemeStore } from '@/stores/theme'
import { fetchMyNotifications } from '@/api/notification'
import type { NotificationItem } from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import { globalSearch } from '@/api/search'
import type { SearchResultItem } from '@/api/search'

const theme = useThemeStore()

interface MenuItem {
  path: string
  title: string
  /** 不填则继承所在分组的 module */
  module?: string
  /** true = 仅管理员可见（如操作日志，后端同样二次校验 403） */
  adminOnly?: boolean
  /** 额外查询参数（如待我审批 tab=approve） */
  query?: Record<string, string>
}

interface MenuGroup {
  module: string
  title: string
  icon: string
  children: MenuItem[]
}

/**
 * 菜单分组：遵循 ITIL/ITSM 领域模型（参考 Jira SM / ServiceNow），非历史路径堆叠。
 * - 组 module = 组内默认权限码；子项可覆盖（如盘点 inventories、合同 contracts）
 * - 无 read 权限整组隐藏；dashboard 恒显示
 * - 权限模型完全不变，仅调整 UI 分组与顺序
 * - 一级分组 ≤ 8 个，符合米勒定律（7±2），降低认知负荷
 */
const menuGroups: MenuGroup[] = [
  {
    module: 'dashboard',
    title: '工作台',
    icon: 'Odometer',
    children: [
      { path: '/dashboard', title: '运营总览' },
      { path: '/workorders', title: '我的工单', module: 'workorders' },
      { path: '/workorders', title: '待我审批', module: 'workorders', query: { tab: 'approve' } },
      { path: '/service-desk', title: '服务目录', module: 'dashboard' },
      // v3.5：通知中心（登录即见，无模块权限门槛；后端 /api/notifications 同样不设 @RequirePerm）
      { path: '/notifications', title: '通知中心', module: 'dashboard' },
    ],
  },
  {
    module: 'assets',
    title: '资产管理',
    icon: 'Box',
    children: [
      { path: '/assets/inventory', title: '资产台账', module: 'assets' },
      { path: '/assets/lifecycle', title: '生命周期', module: 'assets' },
      { path: '/assets/categories', title: '分类与型号', module: 'categories' },
      { path: '/rfid/batches', title: 'RFID 验收', module: 'rfid' },
      { path: '/assets/expiry', title: '到期与续费', module: 'assets' },
      { path: '/assets/quality', title: '数据质量', module: 'assets' },
      { path: '/ci-relations', title: 'CI 关系/影响分析', module: 'assets' },
      { path: '/topology', title: '网络拓扑', module: 'assets' },
    ],
  },
  {
    module: 'discovery',
    title: '配置发现',
    icon: 'Cpu',
    children: [
      { path: '/collector/discovery', title: '自动发现', module: 'discovery' },
      { path: '/collector/endpoints', title: '带外档案', module: 'discovery' },
      { path: '/gpu/cards', title: 'GPU 健康度', module: 'gpu' },
    ],
  },
  {
    module: 'workorders',
    title: '服务管理',
    icon: 'Tickets',
    children: [
      { path: '/service-desk', title: '服务目录', module: 'dashboard' },
      { path: '/workorders', title: '事件/工单', module: 'workorders' },
      { path: '/problems', title: '问题管理', module: 'problems' },
      { path: '/changes', title: '变更管理', module: 'changes' },
      { path: '/knowledge', title: '知识库', module: 'knowledge' },
      { path: '/ops/inspections', title: '巡检计划', module: 'workorders' },
    ],
  },
  {
    module: 'monitoring',
    title: '监控运维',
    icon: 'Monitor',
    children: [
      { path: '/monitoring/overview', title: '监控概览', module: 'monitoring' },
      { path: '/monitoring/alerts', title: '告警中心', module: 'monitoring' },
      { path: '/monitoring/assets', title: '资产监控', module: 'monitoring' },
      { path: '/monitoring/maintenance', title: '维护窗口', module: 'monitoring' },
      { path: '/monitoring/settings', title: '监控设置', module: 'monitoring' },
    ],
  },
  {
    module: 'machineRooms',
    title: '基础设施',
    icon: 'OfficeBuilding',
    children: [
      { path: '/rooms', title: '机房管理', module: 'machineRooms' },
      { path: '/rooms/floor', title: '机房平面', module: 'machineRooms' },
      { path: '/racks', title: '机柜管理', module: 'racks' },
      { path: '/racks/view', title: '机柜视图', module: 'racks' },
      { path: '/capacity', title: '容量管理', module: 'racks' },
      { path: '/network/ipam', title: 'IP 地址管理', module: 'network' },
      { path: '/network/cables', title: '线缆管理', module: 'network' },
    ],
  },
  {
    module: 'materials',
    title: '供应链与财务',
    icon: 'Goods',
    children: [
      { path: '/warehouses', title: '仓库管理', module: 'warehouses' },
      { path: '/materials', title: '物料管理', module: 'materials' },
      { path: '/stock-logs', title: '出入库管理', module: 'stockLogs' },
      { path: '/rfid/stock', title: '库存水位', module: 'materials' },
      { path: '/rfid/scan-in', title: '扫码入库', module: 'stockLogs' },
      { path: '/inventory-tasks', title: '盘点管理', module: 'inventories' },
      { path: '/suppliers/list', title: '供应商管理', module: 'suppliers' },
      { path: '/suppliers/contracts', title: '合同管理', module: 'contracts' },
      { path: '/finance/assets', title: '财务台账', module: 'finance' },
      { path: '/finance/depreciation', title: '折旧底稿', module: 'finance' },
      { path: '/finance/cost-split', title: '成本分摊', module: 'finance' },
    ],
  },
  {
    module: 'report',
    title: '报表分析',
    icon: 'DataAnalysis',
    children: [
      { path: '/reports', title: '数据报表', module: 'report' },
    ],
  },
  {
    module: 'system',
    title: '管理设置',
    icon: 'Setting',
    children: [
      { path: '/system/users', title: '用户管理', module: 'system' },
      { path: '/system/roles', title: '角色与权限', module: 'system' },
      { path: '/basic/dict', title: '数据字典', module: 'system' },
      { path: '/system/logs', title: '操作日志', module: 'system', adminOnly: true },
      { path: '/webhooks', title: '消息推送 / Webhook', module: 'system' },
      { path: '/system/ldap', title: '目录集成（LDAP）', module: 'system', adminOnly: true },
      { path: '/account/tokens', title: '个人访问令牌', module: 'dashboard' },
      { path: '/developer/api-docs', title: '接口文档', module: 'dashboard' },
    ],
  },
]

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const searchKeyword = ref('')
const searchResults = ref<SearchResultItem[]>([])
const showSearch = ref(false)
let searchTimer: number | undefined

/** 搜索请求序号：连续输入触发多次搜索时，仅接受最后一次的响应（丢弃过期结果） */
let searchSeq = 0

async function onSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) {
    searchResults.value = []
    return
  }
  const seq = ++searchSeq
  try {
    const res = await globalSearch(kw)
    if (seq !== searchSeq) return
    searchResults.value = res ?? []
    showSearch.value = true
  } catch {
  }
}

/** 输入防抖 350ms 自动搜索（此前只能回车，且 searchTimer 声明了却从未使用） */
function onSearchInput() {
  window.clearTimeout(searchTimer)
  if (!searchKeyword.value.trim()) {
    searchResults.value = []
    showSearch.value = false
    return
  }
  searchTimer = window.setTimeout(() => void onSearch(), 350)
}

function onSearchSelect(item: SearchResultItem) {
  showSearch.value = false
  searchKeyword.value = ''
  const id = encodeURIComponent(item.id)
  switch (item.type) {
    case 'asset':
      router.push({ path: '/assets/inventory', query: { keyword: item.id } })
      break
    case 'workorder':
      router.push(`/workorders/${id}`)
      break
    case 'room':
      router.push({ path: '/rooms', query: { keyword: item.id } })
      break
    case 'rack':
      router.push({ path: '/racks', query: { keyword: item.id } })
      break
    case 'material':
      router.push({ path: '/rfid/stock', query: { keyword: item.id } })
      break
    case 'change':
      router.push({ path: '/changes', query: { id: item.id } })
      break
    case 'user':
      router.push({ path: '/system/users', query: { keyword: item.id } })
      break
  }
}

const SEARCH_TYPE_LABEL: Record<string, string> = {
  asset: '资产',
  workorder: '工单',
  room: '机房',
  rack: '机柜',
  material: '物料',
  change: '变更',
  user: '用户',
}

/** 组内菜单按各自权限 key 过滤（adminOnly 子项仅管理员显示）；组可见条件：组模块可读或任一子项可读（dashboard 恒显示） */
const visibleGroups = computed(() =>
  menuGroups
    .map((g) => ({
      ...g,
      children: g.children.filter(
        (c) => auth.canRead(c.module || g.module) && (!c.adminOnly || auth.user?.isAdmin),
      ),
    }))
    .filter((g) => auth.canRead(g.module) || g.children.length > 0),
)
const activeMenu = computed(() => route.path)

/** 面包屑：分组名 / 页面名（从当前激活菜单反推） */
const breadcrumb = computed(() => {
  const title = (route.meta.title as string) || ''
  for (const g of visibleGroups.value) {
    const hit = g.children.find((c) => c.path === route.path)
    if (hit) return `${g.title} / ${hit.title || title}`
  }
  return title
})

async function onUserCommand(command: string) {
  if (command === 'password') {
    router.push('/change-password')
  } else if (command === 'tokens') {
    router.push('/account/tokens')
  } else if (command === 'about') {
    void openAbout()
  } else if (command === 'logout') {
    await auth.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}

/* ---------------- v3.4：关于（前后端版本对照） ---------------- */
const aboutVisible = ref(false)
const backendVersion = ref('')
const frontendVersion = __APP_VERSION__

async function openAbout() {
  aboutVisible.value = true
  backendVersion.value = '查询中…'
  try {
    const h = await http.get<any, { version?: string }>('/healthz', { silent: true } as Record<string, unknown>)
    backendVersion.value = h?.version || '未知（v3.3.0 及以前的后端不报告版本）'
  } catch {
    backendVersion.value = '无法获取'
  }
}

/* ---------------- 通知铃铛（v3.5：SSE 实时红点 + toast，轮询降级由 store 承担） ---------------- */
const noti = useNotificationStore()
const notiLoading = ref(false)
const notiList = ref<NotificationItem[]>([])
let offNotification: (() => void) | undefined

async function loadNotiList() {
  notiLoading.value = true
  try {
    const res = await fetchMyNotifications({ page: 1, size: 10 })
    notiList.value = res.records ?? []
  } catch {
    // 拦截器已提示
  } finally {
    notiLoading.value = false
  }
}

function onNotiShow() {
  void loadNotiList()
}

async function onNotiClick(item: NotificationItem) {
  if (item.readFlag) return
  try {
    await noti.markRead(item.id)
    item.readFlag = true
  } catch {
    // 拦截器已提示
  }
}

async function onReadAll() {
  try {
    await noti.markAllRead()
    ElMessage.success('已全部标记为已读')
    void loadNotiList()
  } catch {
    // 拦截器已提示
  }
}

onMounted(() => {
  // 建立 SSE（失败自动退避重连，连续失败降级 30s 轮询）
  noti.start()
  // 实时到达的站内信弹 toast：只有 SSE 实时帧会触发订阅者，历史通知不弹
  offNotification = noti.onNotification((n) => {
    ElNotification({
      title: n.title || '新通知',
      message: n.content || '',
      type: 'info',
      duration: 5000,
      position: 'bottom-right',
    })
  })
})

watch(
  () => route.path,
  () => {
    // 路由切换时对齐一次未读数（跨页面操作后的兜底；SSE 正常时开销可忽略）
    void noti.refreshUnread()
  },
)

onUnmounted(() => {
  offNotification?.()
  noti.stop()
})
</script>

<template>
  <el-container class="layout-root">
    <el-aside width="220px" class="layout-aside">
      <div class="layout-logo">智算资产管理平台</div>
      <el-menu
        class="layout-menu"
        :default-active="activeMenu"
        router
        background-color="#001529"
        text-color="#a6adb4"
        active-text-color="#ffffff"
      >
        <el-sub-menu v-for="group in visibleGroups" :key="group.module" :index="group.module">
          <template #title>
            <el-icon><component :is="group.icon" /></el-icon>
            <span>{{ group.title }}</span>
          </template>
          <el-menu-item v-for="item in group.children" :key="item.path + JSON.stringify(item.query || {})" :index="item.path" :route="{ path: item.path, query: item.query }">
            {{ item.title }}
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="layout-crumb">{{ breadcrumb }}</div>
        <div class="layout-header-center" @mouseleave="showSearch = false">
          <el-input
            v-model="searchKeyword"
            placeholder="全局搜索：资产/工单/机房/机柜/物料/变更..."
            style="width: 320px"
            clearable
            @input="onSearchInput"
            @keyup.enter="onSearch"
            @focus="searchKeyword && onSearch()"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <div v-if="showSearch" class="search-dropdown">
            <div v-if="searchResults.length === 0" class="search-item" style="color: var(--el-text-color-secondary); cursor: default">
              未找到匹配结果
            </div>
            <div
              v-for="item in searchResults"
              :key="item.type + item.id"
              class="search-item"
              @click="onSearchSelect(item)"
            >
              <el-tag size="small" :type="item.type === 'asset' ? 'primary' : item.type === 'workorder' ? 'success' : 'info'" style="margin-right: 8px">
                {{ SEARCH_TYPE_LABEL[item.type] || item.type }}
              </el-tag>
              <span class="search-item-title">{{ item.id }} {{ item.title !== item.id ? item.title : '' }}</span>
              <span v-if="item.status" class="search-item-status">{{ item.status }}</span>
            </div>
          </div>
        </div>
        <div class="layout-header-right">
          <el-popover width="360" trigger="click" @show="onNotiShow">
            <template #reference>
              <span class="layout-bell">
                <el-badge :value="noti.unreadCount" :max="99" :hidden="noti.unreadCount <= 0">
                  <el-icon :size="18"><Bell /></el-icon>
                </el-badge>
              </span>
            </template>
            <div class="noti-panel" v-loading="notiLoading">
              <div class="noti-head">通知</div>
              <div class="noti-list">
                <div
                  v-for="n in notiList"
                  :key="String(n.id)"
                  class="noti-item"
                  :class="{ 'is-unread': !n.readFlag }"
                  @click="onNotiClick(n)"
                >
                  <div class="noti-item-title">
                    <span v-if="!n.readFlag" class="noti-dot"></span>
                    {{ n.title }}
                  </div>
                  <div v-if="n.content" class="noti-item-content">{{ n.content }}</div>
                  <div class="noti-item-time">{{ n.createdAt || '' }}</div>
                </div>
                <el-empty
                  v-if="!notiLoading && notiList.length === 0"
                  description="暂无通知"
                  :image-size="60"
                />
              </div>
              <div class="noti-footer">
                <el-button link type="primary" @click="onReadAll">全部已读</el-button>
                <el-button link type="primary" @click="router.push('/notifications')">查看全部</el-button>
              </div>
            </div>
          </el-popover>
          <el-dropdown trigger="click" @command="onUserCommand">
            <span class="layout-user">
              <el-icon><User /></el-icon>
              <span>{{ auth.user?.name || auth.user?.username || '未登录' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="tokens">个人访问令牌</el-dropdown-item>
                <el-dropdown-item command="about">关于本系统</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <!-- 主题切换 -->
          <el-dropdown trigger="click" :hide-on-click="false">
            <template #reference>
              <span class="layout-theme-toggle" @click="theme.toggle">
                <el-icon :size="18">
                  <component :is="theme.mode === 'dark' ? 'Moon' : theme.mode === 'light' ? 'Sunny' : 'Monitor'" />
                </el-icon>
              </span>
            </template>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click.native="theme.setMode('light')">
                  <template #default>
                    <el-icon><Sunny /></el-icon>
                    <span>浅色模式</span>
                  </template>
                </el-dropdown-item>
                <el-dropdown-item @click.native="theme.setMode('dark')">
                  <template #default>
                    <el-icon><Moon /></el-icon>
                    <span>深色模式</span>
                  </template>
                </el-dropdown-item>
                <el-dropdown-item @click.native="theme.setMode('auto')">
                  <template #default>
                    <el-icon><Monitor /></el-icon>
                    <span>跟随系统</span>
                  </template>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
  <el-dialog v-model="aboutVisible" title="关于本系统" width="420px">
    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="前端版本">{{ frontendVersion }}</el-descriptions-item>
      <el-descriptions-item label="后端版本">{{ backendVersion }}</el-descriptions-item>
    </el-descriptions>
    <p v-if="backendVersion && frontendVersion && backendVersion !== frontendVersion && /^\d/.test(backendVersion)" class="about-warn">
      前后端版本不一致：通常是升级时没有重建全部镜像，或安装目录里残留了旧的后端包。
    </p>
    <template #footer>
      <el-button @click="router.push('/developer/api-docs'); aboutVisible = false">接口文档</el-button>
      <el-button type="primary" @click="aboutVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.layout-root {
  height: 100%;
}

.layout-aside {
  background-color: #001529;
  overflow-x: hidden;
}

.layout-logo {
  height: 56px;
  line-height: 56px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.layout-menu {
  border-right: none;
}

.layout-menu :deep(.el-menu-item.is-active) {
  background-color: var(--el-color-primary);
}

.layout-menu :deep(.el-sub-menu__title:hover),
.layout-menu :deep(.el-menu-item:not(.is-active):hover) {
  background-color: rgba(255, 255, 255, 0.08);
}

.layout-header {
  height: 56px;
  background: var(--el-bg-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.layout-crumb {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.layout-header-center {
  position: relative;
  display: flex;
  align-items: center;
}

.search-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  max-height: 300px;
  overflow-y: auto;
  z-index: 1000;
  margin-top: 4px;
}

.search-item {
  padding: 8px 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  font-size: 13px;
}

.search-item:hover {
  background: var(--el-fill-color-light);
}

.search-item-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-item-status {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: 8px;
}

.layout-header-right {
  display: inline-flex;
  align-items: center;
  gap: 18px;
}

.layout-bell {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  color: var(--el-text-color-primary);
  outline: none;
}

.noti-panel {
  display: flex;
  flex-direction: column;
}

.noti-head {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.noti-list {
  max-height: 320px;
  overflow-y: auto;
}

.noti-item {
  padding: 8px 4px;
  cursor: pointer;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.noti-item:hover {
  background: var(--el-fill-color-light);
}

.noti-item.is-unread .noti-item-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.noti-item-title {
  font-size: 13px;
  color: var(--el-text-color-regular);
  display: flex;
  align-items: center;
}

.noti-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-danger);
  margin-right: 6px;
  flex-shrink: 0;
}

.noti-item-content {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.noti-item-time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 2px;
}

.noti-footer {
  text-align: center;
  padding-top: 6px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.layout-user {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--el-text-color-primary);
  outline: none;
}

.layout-main {
  background: var(--el-fill-color-light);
  padding: 16px;
}
.about-warn {
  margin: 12px 0 0;
  color: var(--el-color-warning);
  font-size: 13px;
}
</style>
