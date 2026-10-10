import type { FormItemRule } from 'element-plus'

/**
 * 密码强度规则 —— 与后端 com.dcim.security.PasswordPolicy 保持一致。
 *
 * 后端在四处校验（创建用户 / 本人改密 / 管理员重置 / CSV 导入），
 * 这里只做「提交前提示」，最终以服务端返回的 400 为准。
 */

/** 最小长度 */
export const PASSWORD_MIN = 12
/** 最大长度 */
export const PASSWORD_MAX = 64

export const PASSWORD_MSG =
  '密码需至少12位，且包含大写/小写/数字/符号中的3类'

/** 命中的字符类别数：大写 / 小写 / 数字 / 符号 */
export function classCount(password: string): number {
  let upper = false
  let lower = false
  let digit = false
  let symbol = false
  for (const ch of password) {
    if (ch >= 'A' && ch <= 'Z') upper = true
    else if (ch >= 'a' && ch <= 'z') lower = true
    else if (ch >= '0' && ch <= '9') digit = true
    else symbol = true
  }
  return Number(upper) + Number(lower) + Number(digit) + Number(symbol)
}

/** 返回错误文案；null 表示通过 */
export function validatePassword(password: string, username?: string): string | null {
  if (!password) return '请输入密码'
  if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) return PASSWORD_MSG
  if (classCount(password) < 3) return PASSWORD_MSG
  const lower = password.toLowerCase()
  if (username && lower.includes(username.toLowerCase())) return '密码不能包含用户名'
  return null
}

/**
 * Element Plus 表单规则。
 * @param username 可选：动态取当前用户名，用于「密码不能包含用户名」联动校验
 */
export function passwordRule(username?: () => string | undefined): FormItemRule {
  return {
    validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
      // 空值交给 required 规则处理，这里不重复报错
      if (!value) {
        callback()
        return
      }
      const message = validatePassword(value, username?.())
      callback(message ? new Error(message) : undefined)
    },
    trigger: 'blur',
  }
}
