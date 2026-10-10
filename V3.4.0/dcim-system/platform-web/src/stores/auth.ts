import { defineStore } from 'pinia'
import { USER_KEY, MODULES_KEY, PERMS_KEY, clearLocalAuth } from '@/api/client'
import * as authApi from '@/api/auth'
import { wecomDevLogin, wecomLogin } from '@/api/wecom'
import type { ActionMap, FieldMap, LoginResult, ModuleMap, UserInfo } from '@/api/types'

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : fallback
  } catch {
    return fallback
  }
}

interface PermExt {
  actions: ActionMap
  fields: FieldMap
  dataScopes: Record<string, string>
}

interface AuthState {
  user: UserInfo | null
  modules: ModuleMap
  perms: PermExt | null
}

function has(list: string[] | undefined, field: string): boolean {
  const f = field.toLowerCase()
  return !!list?.some((x) => x.toLowerCase() === f)
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: readJson<UserInfo | null>(USER_KEY, null),
    modules: readJson<ModuleMap>(MODULES_KEY, {}),
    perms: readJson<PermExt | null>(PERMS_KEY, null),
  }),

  getters: {
    isLoggedIn: (state) => !!state.user,
    canRead: (state) => (moduleCode: string) =>
      moduleCode === 'dashboard' || !!state.modules[moduleCode]?.read,
    canWrite: (state) => (moduleCode: string) => !!state.modules[moduleCode]?.write,
    /** v3.4：删除需显式授予（管理员恒可删除） */
    canDelete: (state) => (moduleCode: string) =>
      !!state.user?.isAdmin || !!state.perms?.actions?.[moduleCode]?.delete,
    /** 操作权限：除删除外默认允许，角色显式禁止时为 false */
    canAction: (state) => (moduleCode: string, action: 'create' | 'update' | 'import' | 'export' | 'approve') =>
      !!state.user?.isAdmin || state.perms?.actions?.[moduleCode]?.[action] !== false,
    /** 字段对当前用户可见 */
    fieldVisible: (state) => (moduleCode: string, field: string) =>
      !!state.user?.isAdmin || !has(state.perms?.fields?.[moduleCode]?.hidden, field),
    /** 字段对当前用户可编辑（模块可写、字段未隐藏且非只读） */
    fieldEditable: (state) => (moduleCode: string, field: string) =>
      !!state.user?.isAdmin ||
      (!!state.modules[moduleCode]?.write &&
        !has(state.perms?.fields?.[moduleCode]?.hidden, field) &&
        !has(state.perms?.fields?.[moduleCode]?.readonly, field)),
    /** 当前用户在该模块上是否有任何字段限制（有则不能批量导入） */
    fieldRestricted: (state) => (moduleCode: string) => {
      if (state.user?.isAdmin) return false
      const r = state.perms?.fields?.[moduleCode]
      return !!(r && ((r.hidden?.length ?? 0) > 0 || (r.readonly?.length ?? 0) > 0))
    },
  },

  actions: {
    persist() {
      if (this.user) localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      else localStorage.removeItem(USER_KEY)
      localStorage.setItem(MODULES_KEY, JSON.stringify(this.modules))
      if (this.perms) localStorage.setItem(PERMS_KEY, JSON.stringify(this.perms))
      else localStorage.removeItem(PERMS_KEY)
    },

    async login(username: string, password: string) {
      const res = await authApi.login(username, password)
      this.user = res.user
      this.modules = {}
      this.persist()
      await this.fetchMe()
    },

    async applyLoginResult(res: LoginResult) {
      this.user = res.user
      this.modules = {}
      this.persist()
      await this.fetchMe()
    },

    async loginWithWecom(code: string) {
      const res = await wecomLogin(code)
      await this.applyLoginResult(res)
    },

    async loginWithDev(username: string) {
      const res = await wecomDevLogin(username)
      await this.applyLoginResult(res)
    },

    async refreshTokens() {
      await authApi.refresh()
    },

    async fetchMe() {
      const me = await authApi.fetchMe()
      this.user = {
        username: me.username,
        name: me.name,
        isAdmin: me.isAdmin,
        mustChangePwd: me.mustChangePwd,
        roles: me.roles,
      }
      this.modules = me.modules || {}
      this.perms = { actions: me.actions || {}, fields: me.fields || {}, dataScopes: me.dataScopes || {} }
      this.persist()
    },

    async ensureLoaded() {
      if (!this.modules || Object.keys(this.modules).length === 0 || !this.perms) {
        await this.fetchMe()
      }
    },

    async logout() {
      try {
        await authApi.logout()
      } catch {
      }
      this.reset()
    },

    reset() {
      this.user = null
      this.modules = {}
      this.perms = null
      clearLocalAuth()
    },
  },
})
