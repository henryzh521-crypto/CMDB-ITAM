/** 统一用户信息 */
export interface UserInfo {
  username: string
  name: string
  isAdmin: boolean
  mustChangePwd?: boolean
  roles?: string[]
}

/** 模块权限（读/写） */
export interface ModulePerm {
  read: boolean
  write: boolean
}

/** <模块code> -> 读写权限 */
export type ModuleMap = Record<string, ModulePerm>

export interface LoginResult {
  user: UserInfo
}

/** v3.4：每个模块的操作权限（后端已按角色合并；删除需显式授予） */
export type ActionMap = Record<string, Partial<Record<'delete' | 'create' | 'update' | 'import' | 'export' | 'approve', boolean>>>

/** v3.4：字段级权限（隐藏 = 不可见不可写；只读 = 可见不可写） */
export interface FieldRule {
  hidden?: string[]
  readonly?: string[]
}
export type FieldMap = Record<string, FieldRule>

export interface MeResult extends UserInfo {
  roles: string[]
  modules: ModuleMap
  actions?: ActionMap
  fields?: FieldMap
  dataScopes?: Record<string, string>
}

/** 通用分页返回 */
export interface PageResult<T> {
  total: number
  records: T[]
}

/** CSV 批量导入结果（部分成功语义） */
export interface CsvImportResult {
  total: number
  success: number
  failed: number
  errors: { row: number; reason: string }[]
}

/** 操作日志行（audit_log，仅管理员可查） */
export interface AuditLogItem {
  id: number
  actor: string
  action: string
  module: string
  objType?: string
  objId?: string
  ip?: string
  detail?: Record<string, unknown>
  createdAt?: string
}

/** 操作日志筛选下拉候选 */
export interface AuditLogOptions {
  modules: string[]
  actions: string[]
  objTypes: string[]
  actors: string[]
}

export interface UserItem {
  username: string
  name: string
  department?: string
  phone?: string
  email?: string
  status?: string | number
  mustChangePwd?: boolean
  roles?: string[]
  createdAt?: string
  /** v3.4：账号级登录锁定 */
  loginLocked?: boolean
  loginLockedSeconds?: number
}

export interface RoleItem {
  roleCode: string
  name: string
  description?: string
  builtIn?: boolean
  modulePerms?: Record<string, ModulePerm>
  dataScopes?: Record<string, unknown>
  fieldPerms?: Record<string, unknown>
  actionPerms?: Record<string, Record<string, boolean>>
}

export interface ModuleInfo {
  code: string
  name: string
  group: string
}
