import { SWAP_OUT_STATUSES } from '@/api/workorder'
import type { WoReplacement } from '@/api/workorder'

/**
 * 子件更换明细（故障维修 / 部件更换）——创建弹窗与详情页共用的一套行模型与校验。
 *
 * 后端单一权威：`workorder.replacements` JSONB，由 `normalizeAndValidateReplacements` 校验。
 * 这里是它的**前端镜像**，只做「提交前先给中文提示」，规则与后端逐条对齐：
 * 三键必填 / 父资产须在关联资产 / 父旧新互不相同 / 同单旧件新件不可重复 / 换下后状态白名单。
 */

/** 子件更换明细编辑行（`key` 仅用于前端行标识与选项缓存，提交前剥离） */
export interface ReplacementRow {
  key: number
  parentAssetNo: string
  oldAssetNo: string
  newAssetNo: string
  /** 旧件换下后状态；空串 = 执行时回退「维修中」 */
  oldStatus: string
}

let rowKeySeq = 0

export function newReplacementRow(parentAssetNo = ''): ReplacementRow {
  rowKeySeq += 1
  return {
    key: rowKeySeq,
    parentAssetNo,
    oldAssetNo: '',
    newAssetNo: '',
    oldStatus: '维修中',
  }
}

/** 行是否已有输入（三个资产字段任一非空）。全空行在提交时忽略，不报错。 */
export function isRowTouched(row: ReplacementRow): boolean {
  return !!(row.parentAssetNo || row.oldAssetNo || row.newAssetNo)
}

/**
 * 提交前校验，返回中文错误信息；通过返回 null。
 * @param parentOptions 允许的父资产（详情页=关联资产；创建页=已添加的关联资产行）
 */
export function validateReplacementRows(
  rows: ReplacementRow[],
  parentOptions: string[],
): string | null {
  const active = rows.filter(isRowTouched)
  const seenOld = new Set<string>()
  const seenNew = new Set<string>()
  const allowed = SWAP_OUT_STATUSES as readonly string[]

  for (let i = 0; i < active.length; i++) {
    const r = active[i]
    const at = i + 1
    if (!r.parentAssetNo || !r.oldAssetNo || !r.newAssetNo) {
      return `第 ${at} 行请补齐 父资产/旧件/新件`
    }
    if (!parentOptions.includes(r.parentAssetNo)) {
      return `第 ${at} 行父资产须在「关联资产」中：${r.parentAssetNo}（该设备可能已被移出本单）`
    }
    if (
      r.oldAssetNo === r.newAssetNo ||
      r.oldAssetNo === r.parentAssetNo ||
      r.newAssetNo === r.parentAssetNo
    ) {
      return `第 ${at} 行父/旧/新资产不能相同`
    }
    if (r.oldStatus && !allowed.includes(r.oldStatus)) {
      return `第 ${at} 行换下后状态不合法（仅 ${SWAP_OUT_STATUSES.join('/')}）`
    }
    if (seenOld.has(r.oldAssetNo)) {
      return `旧件重复：${r.oldAssetNo}（同一旧件只能换一次）`
    }
    if (seenNew.has(r.newAssetNo)) {
      return `新件重复：${r.newAssetNo}（同一件新备件只能装一处）`
    }
    seenOld.add(r.oldAssetNo)
    seenNew.add(r.newAssetNo)
  }
  return null
}

/** 行 → 后端契约（丢弃未填完的行，剥离 key） */
export function toWoReplacements(rows: ReplacementRow[]): WoReplacement[] {
  return rows
    .filter((r) => r.parentAssetNo && r.oldAssetNo && r.newAssetNo)
    .map((r) => ({
      parentAssetNo: r.parentAssetNo,
      oldAssetNo: r.oldAssetNo,
      newAssetNo: r.newAssetNo,
      oldStatus: r.oldStatus || undefined,
    }))
}

/**
 * 由明细派生主表 `replaced_asset`（旧件号逗号连接）。
 * 让「更换部件」单值字段与结构化明细始终一致，不再出现两套各填各的。
 */
export function deriveReplacedAsset(rows: ReplacementRow[]): string {
  return toWoReplacements(rows)
    .map((r) => r.oldAssetNo)
    .join(',')
}
