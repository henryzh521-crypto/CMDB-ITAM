import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// 所有视图懒加载：业务页面按需分包，避免全量打进主 chunk（首屏只需布局 + 仪表盘）
const MainLayout = () => import('@/layout/MainLayout.vue')
const MobileLayout = () => import('@/layout/MobileLayout.vue')

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录', public: true } },
  {
    path: '/change-password',
    name: 'change-password',
    component: () => import('@/views/ChangePasswordView.vue'),
    meta: { title: '修改密码' },
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      // 仪表盘（P6 报表域：/api/report/dashboard 聚合；module=dashboard 恒可见，不绑 report）
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/report/DashboardView.vue'),
        meta: { title: '运营总览', module: 'dashboard' },
      },

      // 机房动力（机房 / 机柜为真实页面）
      {
        path: 'rooms',
        name: 'room-list',
        component: () => import('@/views/room/RoomListView.vue'),
        meta: { title: '机房管理', module: 'machineRooms' },
      },
      {
        path: 'rooms/floor',
        name: 'room-floor',
        component: () => import('@/views/room/RoomFloorView.vue'),
        meta: { title: '机房平面', module: 'machineRooms' },
      },
      {
        path: 'racks',
        name: 'rack-list',
        component: () => import('@/views/room/RackListView.vue'),
        meta: { title: '机柜管理', module: 'racks' },
      },
      {
        path: 'racks/view',
        name: 'rack-elevation',
        component: () => import('@/views/room/RackElevationView.vue'),
        meta: { title: '机柜视图', module: 'racks' },
      },

      // 资产管理 assets（台账/分类/RFID 验收为真实页面）
      {
        path: 'assets/inventory',
        name: 'asset-inventory',
        component: () => import('@/views/asset/AssetListView.vue'),
        meta: { title: '资产台账', module: 'assets' },
      },
      {
        path: 'assets/expiry',
        name: 'asset-expiry',
        component: () => import('@/views/asset/ExpiryView.vue'),
        meta: { title: '到期提醒', module: 'assets' },
      },
      {
        path: 'capacity',
        name: 'capacity',
        component: () => import('@/views/capacity/CapacityView.vue'),
        meta: { title: '容量管理', module: 'racks' },
      },
      {
        path: 'network/ipam',
        name: 'network-ipam',
        component: () => import('@/views/network/IpamView.vue'),
        meta: { title: 'IP 地址管理', module: 'network' },
      },
      {
        path: 'network/cables',
        name: 'network-cables',
        component: () => import('@/views/network/CableView.vue'),
        meta: { title: '线缆管理', module: 'network' },
      },
      {
        path: 'assets/lifecycle',
        name: 'asset-lifecycle',
        component: () => import('@/views/asset/AssetLifecycleView.vue'),
        meta: { title: '资产生命周期', module: 'assets' },
      },
      {
        path: 'assets/categories',
        name: 'asset-categories',
        component: () => import('@/views/masterdata/CategoryView.vue'),
        meta: { title: '资产分类', module: 'categories' },
      },
      // 型号库已下线：老链接（含旧「品牌型号」）统一跳分类页
      { path: 'assets/models', redirect: '/assets/categories' },

      // 仓储供应 materials / stockLogs / inventories
      // （库存水位/扫码入库路径沿用旧 URL 以兼容书签）
      // 供应商列表/合同管理已迁入侧边栏「供应商管理」组（已替换占位为真实页面）
      {
        path: 'warehouses',
        name: 'warehouse-list',
        component: () => import('@/views/warehouse/WarehouseListView.vue'),
        meta: { title: '仓库管理', module: 'warehouses' },
      },
      {
        path: 'materials',
        name: 'material-list',
        component: () => import('@/views/warehouse/MaterialListView.vue'),
        meta: { title: '物料管理', module: 'materials' },
      },
      {
        path: 'stock-logs',
        name: 'stock-log-list',
        component: () => import('@/views/warehouse/StockLogListView.vue'),
        meta: { title: '出入库管理', module: 'stockLogs' },
      },
      {
        path: 'inventory-tasks',
        name: 'inventory-task-list',
        component: () => import('@/views/warehouse/InventoryTaskListView.vue'),
        meta: { title: '盘点管理', module: 'inventories' },
      },
      {
        path: 'rfid/stock',
        name: 'stock-level',
        component: () => import('@/views/warehouse/StockLevelView.vue'),
        meta: { title: '库存水位', module: 'materials' },
      },
      {
        path: 'rfid/scan-in',
        name: 'scan-in',
        component: () => import('@/views/warehouse/ScanInView.vue'),
        meta: { title: '扫码入库', module: 'stockLogs' },
      },
      // 供应商管理：列表 module=suppliers；合同 module=contracts（RBAC 独立）
      {
        path: 'suppliers/list',
        name: 'supplier-list',
        component: () => import('@/views/supplier/SupplierListView.vue'),
        meta: { title: '供应商列表', module: 'suppliers' },
      },
      {
        path: 'suppliers/contracts',
        name: 'contract-list',
        component: () => import('@/views/supplier/ContractListView.vue'),
        meta: { title: '合同管理', module: 'contracts' },
      },

      // 运维工单 workorders（工单管理 / 配置 / 详情 / 巡检计划）
      {
        path: 'workorders',
        name: 'workorder-list',
        component: () => import('@/views/workorder/WorkorderListView.vue'),
        meta: { title: '工单管理', module: 'workorders' },
      },
      {
        path: 'workorders/settings',
        name: 'workorder-settings',
        component: () => import('@/views/workorder/WorkorderSettingsView.vue'),
        meta: { title: '工单配置', module: 'workorders' },
      },
      {
        path: 'workorders/:woNo',
        name: 'workorder-detail',
        component: () => import('@/views/workorder/WorkorderDetailView.vue'),
        meta: { title: '工单详情', module: 'workorders' },
      },
      {
        path: 'ops/inspections',
        name: 'inspection-plans',
        component: () => import('@/views/workorder/InspectionPlanView.vue'),
        meta: { title: '巡检计划', module: 'workorders' },
      },

      // 财务管理 finance（财务台账 / 折旧底稿 / 成本分摊）
      {
        path: 'finance/assets',
        name: 'finance-assets',
        component: () => import('@/views/finance/FinanceAssets.vue'),
        meta: { title: '财务台账', module: 'finance' },
      },
      {
        path: 'finance/depreciation',
        name: 'finance-depreciation',
        component: () => import('@/views/finance/DepreciationSheet.vue'),
        meta: { title: '折旧底稿', module: 'finance' },
      },
      {
        path: 'finance/cost-split',
        name: 'finance-cost-split',
        component: () => import('@/views/finance/CostAllocationView.vue'),
        meta: { title: '成本分摊', module: 'finance' },
      },
      // 计费账单：无对应数据模型的死菜单，已下线，老链接回折旧底稿
      { path: 'finance/bills', redirect: '/finance/depreciation' },

      // RFID 批次验收（资产域真实页面；扫码入库/库存水位已归仓储供应，路径不变）
      {
        path: 'rfid/batches',
        name: 'rfid-batch-list',
        component: () => import('@/views/rfid/RfidBatchListView.vue'),
        meta: { title: 'RFID 批次验收', module: 'rfid' },
      },

      // 采集自动化 discovery / gpu（自动发现/带外档案/GPU 健康度为真实页面，W2）
      {
        path: 'collector/discovery',
        name: 'discover-task-list',
        component: () => import('@/views/collector/DiscoverTaskListView.vue'),
        meta: { title: '自动发现', module: 'discovery' },
      },
      {
        path: 'collector/endpoints',
        name: 'oob-endpoint-list',
        component: () => import('@/views/collector/EndpointListView.vue'),
        meta: { title: '带外档案', module: 'discovery' },
      },
      {
        path: 'gpu/cards',
        name: 'gpu-card-list',
        component: () => import('@/views/gpu/GpuCardListView.vue'),
        meta: { title: 'GPU 健康度', module: 'gpu' },
      },

      // 报表中心 report（运营总览入口在工作台，此处仅数据报表，避免双入口）
      {
        path: 'reports',
        name: 'report-center',
        component: () => import('@/views/report/ReportView.vue'),
        meta: { title: '数据报表', module: 'report' },
      },

      // 旧「基础数据」死菜单：品牌型号（型号库已下线）→ 资产分类；数据字典（path 兼容旧链接）
      { path: 'basic/models', redirect: '/assets/categories' },
      {
        path: 'basic/dict',
        name: 'basic-dict',
        component: () => import('@/views/system/DictView.vue'),
        meta: { title: '数据字典', module: 'system' },
      },

      // v3.4：接口文档（登录即可访问；后端 /api/openapi.json）
      {
        path: 'developer/api-docs',
        name: 'developer-api-docs',
        component: () => import('@/views/developer/ApiDocsView.vue'),
        meta: { title: '接口文档', module: 'dashboard' },
      },
      // 系统管理 system（用户/角色为真实页面）
      {
        path: 'system/users',
        name: 'system-users',
        component: () => import('@/views/system/UsersView.vue'),
        meta: { title: '用户管理', module: 'system' },
      },
      {
        path: 'system/roles',
        name: 'system-roles',
        component: () => import('@/views/system/RolesView.vue'),
        meta: { title: '角色管理', module: 'system' },
      },
      // 操作日志：仅管理员（adminOnly 由守卫拦截，后端仍双保险 403）
      {
        path: 'system/logs',
        name: 'system-logs',
        component: () => import('@/views/system/AuditLogView.vue'),
        meta: { title: '操作日志', module: 'system', adminOnly: true },
      },

      // 变更管理
      {
        path: 'changes',
        name: 'change-list',
        component: () => import('@/views/change/ChangeListView.vue'),
        meta: { title: '变更管理', module: 'changes' },
      },

      // CI 关系管理
      {
        path: 'ci-relations',
        name: 'ci-relation-list',
        component: () => import('@/views/ci/CiRelationView.vue'),
        meta: { title: 'CI 关系与影响分析', module: 'assets' },
      },

      // v3.5：全量网络拓扑（机房过滤 + 关系图例 + 节点跳转）
      {
        path: 'topology',
        name: 'ci-topology',
        component: () => import('@/views/ci/TopologyView.vue'),
        meta: { title: '网络拓扑', module: 'assets' },
      },

      // 平台：个人令牌 / 目录集成 / 数据质量
      {
        path: 'account/tokens',
        name: 'account-tokens',
        component: () => import('@/views/account/TokenView.vue'),
        meta: { title: '个人访问令牌', module: 'dashboard' },
      },
      {
        path: 'system/ldap',
        name: 'system-ldap',
        component: () => import('@/views/system/LdapView.vue'),
        meta: { title: '目录集成', module: 'system', adminOnly: true },
      },
      {
        path: 'assets/quality',
        name: 'asset-quality',
        component: () => import('@/views/asset/DataQualityView.vue'),
        meta: { title: '数据质量', module: 'assets' },
      },
      // 服务台 / 问题 / 知识库
      {
        path: 'service-desk',
        name: 'service-desk',
        component: () => import('@/views/catalog/ServicePortalView.vue'),
        meta: { title: '服务台', module: 'dashboard' },
      },
      {
        path: 'problems',
        name: 'problems',
        component: () => import('@/views/problem/ProblemView.vue'),
        meta: { title: '问题管理', module: 'problems' },
      },
      {
        path: 'knowledge',
        name: 'knowledge',
        component: () => import('@/views/knowledge/KbView.vue'),
        meta: { title: '知识库', module: 'knowledge' },
      },
      // 监控告警
      {
        path: 'monitoring/alerts',
        name: 'monitoring-alerts',
        component: () => import('@/views/monitoring/AlertCenterView.vue'),
        meta: { title: '告警中心', module: 'monitoring' },
      },
      {
        path: 'monitoring/overview',
        name: 'monitoring-overview',
        component: () => import('@/views/monitoring/OverviewView.vue'),
        meta: { title: '监控概览', module: 'monitoring' },
      },
      {
        path: 'monitoring/assets',
        name: 'monitoring-assets',
        component: () => import('@/views/monitoring/AssetMetricsView.vue'),
        meta: { title: '资产监控', module: 'monitoring' },
      },
      {
        path: 'monitoring/maintenance',
        name: 'monitoring-maintenance',
        component: () => import('@/views/monitoring/MaintenanceView.vue'),
        meta: { title: '维护窗口', module: 'monitoring' },
      },
      {
        path: 'monitoring/settings',
        name: 'monitoring-settings',
        component: () => import('@/views/monitoring/SettingsView.vue'),
        meta: { title: '监控设置', module: 'monitoring' },
      },

      // Webhook 管理
      {
        path: 'webhooks',
        name: 'webhook-list',
        component: () => import('@/views/webhook/WebhookListView.vue'),
        meta: { title: '消息推送 / Webhook', module: 'system' },
      },

      // v3.5：通知中心（登录即可访问；module=dashboard 恒可见，与后端 /api/notifications 不设权限门槛对齐）
      {
        path: 'notifications',
        name: 'notification-center',
        component: () => import('@/views/system/NotificationCenterView.vue'),
        meta: { title: '通知中心', module: 'dashboard' },
      },
    ],
  },
  // 企微移动端 H5（独立移动布局 + 底部 tab 导航）
  {
    path: '/m',
    component: MobileLayout,
    redirect: '/m/assets',
    meta: { layout: 'mobile' },
    children: [
      {
        path: 'login',
        name: 'm-login',
        component: () => import('@/views/mobile/MLogin.vue'),
        meta: { title: '登录', public: true },
      },
      {
        path: 'assets',
        name: 'm-assets',
        component: () => import('@/views/mobile/MAssetLookup.vue'),
        meta: { title: '查资产', module: 'assets' },
      },
      {
        path: 'inventory',
        name: 'm-inventory',
        component: () => import('@/views/mobile/MInventory.vue'),
        meta: { title: '盘点', module: 'inventories' },
      },
      {
        path: 'approvals',
        name: 'm-approvals',
        component: () => import('@/views/mobile/MApprovals.vue'),
        meta: { title: '审批', module: 'workorders' },
      },
      {
        path: 'me',
        name: 'm-me',
        component: () => import('@/views/mobile/MMe.vue'),
        meta: { title: '我的' },
      },
      {
        path: 'change-password',
        name: 'm-change-password',
        component: () => import('@/views/mobile/MChangePassword.vue'),
        meta: { title: '修改密码' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 文档标题跟随路由（便于多标签识别当前页）
router.afterEach((to) => {
  const t = (to.meta.title as string) || ''
  document.title = t ? `${t} · 智算资产管理平台` : '智算资产管理平台'
})

/** 登录态补全失败时区分错误来源：
 *  - 401 由 api 拦截器统一处理（清态 + 整页跳登录），守卫保持原行为即可；
 *  - 其余（后端不可达 / 网络错误）需清理本地残留登录态，否则 /login 守卫
 *    看到 isLoggedIn 又把人弹回受保护页，形成「重定向死循环 + 请求风暴」白屏。 */
function isUnauthorized(e: unknown): boolean {
  return (e as { response?: { status?: number } } | null)?.response?.status === 401
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // 移动端路由：同一 token 校验，未登录跳 /m/login
  if (to.path === '/m' || to.path.startsWith('/m/')) {
    if (to.meta.public) {
      return auth.isLoggedIn ? '/m/assets' : true
    }
    if (!auth.isLoggedIn) {
      return { path: '/m/login', query: { redirect: to.fullPath } }
    }
    try {
      await auth.ensureLoaded()
    } catch (e) {
      if (!isUnauthorized(e)) auth.reset()
      return '/m/login'
    }
    // 首次登录必须改密（后端 JwtAuthFilter 也已强制，这里只负责把人送到改密页）
    if (auth.user?.mustChangePwd && to.path !== '/m/change-password') {
      return '/m/change-password'
    }
    return true
  }

  if (to.path === '/login') {
    return auth.isLoggedIn ? '/dashboard' : true
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: to.path === '/dashboard' ? {} : { redirect: to.fullPath } }
  }

  // 已登录但权限未加载：自动调用 /api/auth/me 补全
  try {
    await auth.ensureLoaded()
  } catch (e) {
    // 非 401（后端不可达等）：清残留登录态再去登录页，避免 /login 守卫弹回造成死循环
    if (!isUnauthorized(e)) auth.reset()
    return '/login'
  }

  // 首次登录必须改密
  if (auth.user?.mustChangePwd && to.path !== '/change-password') {
    return '/change-password'
  }

  // 模块权限守卫：meta.module 有值且无 read → 回工作台（后端 403 仍兜底）
  const mod = to.meta.module as string | undefined
  if (mod && !auth.canRead(mod) && to.path !== '/dashboard') {
    return '/dashboard'
  }

  // 仅管理员页面（操作日志）：非管理员回工作台（后端仍二次校验 403）
  if (to.meta.adminOnly && !auth.user?.isAdmin && to.path !== '/dashboard') {
    return '/dashboard'
  }
  return true
})

export default router
