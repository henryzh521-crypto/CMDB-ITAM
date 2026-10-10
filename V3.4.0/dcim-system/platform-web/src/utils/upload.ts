import { ElMessage } from 'element-plus'

/** CSV 批量导入前端预校验：大小上限 10MB，超限直接提示、不发请求 */
export const CSV_IMPORT_MAX_SIZE = 10 * 1024 * 1024

/** 导入前校验 CSV 大小，通过返回 true；超限弹提示并返回 false */
export function checkCsvImportSize(file: File): boolean {
  if (file.size > CSV_IMPORT_MAX_SIZE) {
    ElMessage.error('CSV 文件不能超过 10MB，请拆分后分批导入')
    return false
  }
  return true
}
