import axios from 'axios'
import type { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 静默请求：失败时不弹全局错误提示（由调用方自行降级） */
    silent?: boolean
  }
}

/** 该请求是否要求静默失败（不弹错误提示） */
function isSilent(cfg: unknown): boolean {
  return !!(cfg as { silent?: boolean } | undefined)?.silent
}

export const USER_KEY = 'platform.user'
export const MODULES_KEY = 'platform.modules'
export const PERMS_KEY = 'platform.perms'

export function clearLocalAuth(): void {
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(MODULES_KEY)
  localStorage.removeItem(PERMS_KEY)
}

function redirectToLogin(): void {
  // 移动端路由区 401 分流到移动登录页，避免跳到桌面登录。
  // 必须匹配 "/m" 或 "/m/..."——直接 startsWith('/m') 会把桌面端的
  // "/materials"（物料管理）误判成移动端，401 时把人甩到移动登录页。
  const p = window.location.pathname
  const onMobile = p === '/m' || p.startsWith('/m/')
  const loginPath = onMobile ? '/m/login' : '/login'
  if (!p.startsWith(loginPath)) {
    // 携带当前完整路径供登录成功后回跳（登录页已有 redirect query 处理）；
    // 已在登录页时不再跳转，防止重复重定向。
    const redirect = encodeURIComponent(p + window.location.search + window.location.hash)
    window.location.href = `${loginPath}?redirect=${redirect}`
  }
}

/** 不参与自动续期的端点（登录/刷新本身/企微登录）——否则会递归刷新 */
const NO_REFRESH_URLS = ['/auth/login', '/auth/refresh', '/auth/wecom/']

type RetryConfig = InternalAxiosRequestConfig & { _retried?: boolean }

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true,
})

/**
 * 专用实例做令牌刷新：不挂任何拦截器，
 * 避免“刷新请求自身 401 → 再次触发刷新”的无限递归。
 */
const authHttp = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

/** 单飞（single-flight）：并发 401 只发一次 refresh，其余复用同一 Promise */
let refreshPromise: Promise<string | null> | null = null

function doRefresh(): Promise<string | null> {
  return authHttp
    .post('/auth/refresh')
    .then((res) => {
      const body = res.data as { code?: number; data?: unknown } | null
      if (body && body.code === 0) {
        return 'ok'
      }
      return null
    })
    .catch(() => null)
}

function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

/** 401 → 续期 → 重放原请求（cookie 自动携带）；续期失败则清态跳登录 */
async function retryAfterRefresh(config: RetryConfig, original: AxiosError): Promise<unknown> {
  const result = await refreshAccessToken()
  if (!result) {
    clearLocalAuth()
    redirectToLogin()
    throw original
  }
  return http.request(config)
}

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  return config
})

http.interceptors.response.use(
  (response: AxiosResponse) => {
    const body = response.data
    // 统一响应 { code, msg, data }：成功时直接解包 data 载荷返回，
    // 与 api 层 http.get<any, T> 的泛型契约一致（运行时即 T 本身）。
    // blob / 非该结构响应原样透传完整 response（调用方取 res.data）。
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data as unknown as AxiosResponse
      }
      if (body.code === 401) {
        redirectToLogin()
      }
      if (!isSilent(response.config)) {
        ElMessage.error(body.msg || '请求失败')
      }
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return response
  },
  async (error: AxiosError) => {
    const config = error.config as RetryConfig | undefined
    const status = error.response?.status
    const url = config?.url ?? ''
    const isAuthEndpoint = NO_REFRESH_URLS.some((u) => url.includes(u))
    const shouldRefresh =
      status === 401 && !!config && !config._retried && !isAuthEndpoint

    if (shouldRefresh) {
      config!._retried = true
      try {
        return await retryAfterRefresh(config!, error)
      } catch (e) {
        // e !== error：重放请求自身失败，已由重放时走过的本拦截器弹过一次，
        // 这里再弹就是「续期后重放重复弹错」；直接把重放错误交给调用方。
        if (e !== error) {
          return Promise.reject(e)
        }
        // e === error：单飞续期拿不到 token（retryAfterRefresh 内已清态跳登录），
        // 落到下面统一的 401 分支再收口一次（清态幂等 + 弹一次过期提示）。
      }
    }

    const msg = (error.response?.data as { msg?: string } | undefined)?.msg
    if (status === 401) {
      if (isAuthEndpoint) {
        ElMessage.error(msg || '用户名或密码错误')
      } else {
        redirectToLogin()
        ElMessage.error(msg || '登录已过期，请重新登录')
      }
    } else {
      if (!isSilent(config)) {
        ElMessage.error(msg || error.message || '网络异常')
      }
    }
    return Promise.reject(error)
  },
)

export default http
