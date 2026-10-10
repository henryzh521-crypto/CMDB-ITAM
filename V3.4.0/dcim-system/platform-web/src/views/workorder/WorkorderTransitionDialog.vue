<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDictOptions } from '@/api/dict'
import type { DictOption } from '@/api/dict'
import { REPAIR_WO_TYPES, SWAP_WO_TYPES, transitionWorkorder } from '@/api/workorder'
import type { WoReplacement, WoTransitionPayload } from '@/api/workorder'

/** 根因分类字典（V29 种子 18 项，可在 系统管理→数据字典 wo_root_cause 增删） */
const ROOT_CAUSE_DICT = 'wo_root_cause'
/** 维修费用单笔上限，与后端 WorkorderService.MAX_COST 对齐 */
const MAX_COST = 99999999.99

/**
 * 工单流转确认弹窗（从详情页调用）。
 *
 * 详情页通过 `ref.open(action)` 打开；确认后调用后端流转接口，
 * 成功后 emit `done` 让详情页重新拉取。
 */
const props = defineProps<{
  woNo: string
  /** 工单类型（故障维修/部件更换完成时才显示更换部件输入） */
  woType?: string
  /** 当前状态：只有「处理中 → 关闭」才必填根因（已驳回 → 关闭 是申请人撤单） */
  status?: string
  /** 详情中已填写的更换部件，作为默认值 */
  replacedAsset?: string
  /** 详情页结构化换件明细：一单换多件时改用明细汇总展示，不再给单值输入框 */
  replacements?: WoReplacement[]
}>()

const emit = defineEmits<{ (e: 'done'): void }>()

const visible = ref(false)
const saving = ref(false)
const actionName = ref('')
const form = reactive({
  opinion: '',
  rootCause: '',
  replacedAsset: '',
  completeNote: '',
  costLabor: 0,
  costService: 0,
  costOther: 0,
})

/* ── 根因下拉（字典项，不可自由输入，保证根因分布统计口径不散） ── */
const rootCauseOptions = ref<DictOption[]>([])
const rootCauseLoading = ref(false)

async function loadRootCauseOptions() {
  if (rootCauseOptions.value.length || rootCauseLoading.value) return
  rootCauseLoading.value = true
  try {
    rootCauseOptions.value = (await fetchDictOptions(ROOT_CAUSE_DICT)) ?? []
  } catch {
    // 拦截器已提示；失败时下拉为空并给占位提示
    rootCauseOptions.value = []
  } finally {
    rootCauseLoading.value = false
  }
}

/** 维修类工单（故障维修/部件更换） */
const isRepairWo = computed(
  () => (REPAIR_WO_TYPES as readonly string[]).includes(props.woType || '')
)

/** 完成工单 + 维修类：顺带登记根因与三笔费用（其余动作/类型不发这些字段） */
const showRepairFields = computed(() => actionName.value === '完成工单' && isRepairWo.value)

/** 结构化明细（有）时的只读汇总；空则维持原单值输入 */
const hasReplacementDetails = computed(
  () => (SWAP_WO_TYPES as readonly string[]).includes(props.woType || '')
    && actionName.value === '完成工单'
    && (props.replacements?.length ?? 0) > 0,
)

const replacementSummary = computed(() =>
  (props.replacements ?? []).map(
    (r) => `${r.parentAssetNo}：${r.oldAssetNo} → ${r.newAssetNo}（${r.oldStatus || '维修中'}）`,
  ),
)

/** 明细模式下主表「更换部件」的取值：旧件号逗号连接（与创建时派生口径一致） */
function detailReplacedAsset() {
  return (props.replacements ?? [])
    .map((r) => r.oldAssetNo)
    .filter(Boolean)
    .join(',')
}

/** 根因下拉出现的场景：关闭（全类型，处理中→关闭必填）、维修类完成工单（选填） */
const showRootCause = computed(() => actionName.value === '关闭' || showRepairFields.value)

const rootCausePlaceholder = computed(() => {
  if (actionName.value === '关闭') {
    return props.status === '处理中' ? '关闭根因（必填，从字典选择）' : '关闭根因（选填，从字典选择）'
  }
  return '根因分类（选填，完成时落库）'
})

function open(action: string) {
  actionName.value = action
  form.opinion = ''
  form.rootCause = ''
  form.completeNote = ''
  form.replacedAsset = props.replacedAsset || ''
  form.costLabor = 0
  form.costService = 0
  form.costOther = 0
  visible.value = true
  if (showRootCause.value) void loadRootCauseOptions()
}

async function submit() {
  const action = actionName.value
  const opinion = form.opinion.trim()
  const rootCause = form.rootCause.trim()
  const completeNote = form.completeNote.trim()
  if (action === '驳回' && !opinion) {
    ElMessage.warning('驳回必须填写审批意见')
    return
  }
  // 「处理中 → 关闭」必填根因；「已驳回 → 关闭」是申请人撤单，旧规则不要求
  const rootCauseRequired = action === '关闭' && props.status === '处理中'
  if (rootCauseRequired && !rootCause) {
    ElMessage.warning('关闭必须填写根因')
    return
  }
  if (rootCause && !rootCauseOptions.value.some((o) => o.dictKey === rootCause)) {
    ElMessage.warning('根因必须从字典项中选择')
    return
  }
  const replacedAsset = hasReplacementDetails.value
    ? detailReplacedAsset()
    : form.replacedAsset.trim()
  const payload: WoTransitionPayload = {
    action,
    opinion: opinion || undefined,
    rootCause: rootCause || undefined,
    replacedAsset: action === '完成工单' && replacedAsset ? replacedAsset : undefined,
    completeNote: action === '完成工单' && completeNote ? completeNote : undefined,
  }
  if (showRepairFields.value) {
    payload.costLabor = form.costLabor
    payload.costService = form.costService
    payload.costOther = form.costOther
  }
  saving.value = true
  try {
    await transitionWorkorder(props.woNo, payload)
    ElMessage.success(`已执行「${action}」`)
    visible.value = false
    emit('done')
  } catch {
    // 拦截器已提示（409 非法流转 / 403 无权限）
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="`确认「${actionName}」`"
    width="480px"
    append-to-body
    destroy-on-close
  >
    <el-form label-width="92px">
      <!-- 根因：关闭（全类型）/ 维修类完成工单 —— 只能选字典项，保证分布统计口径不散 -->
      <el-form-item
        v-if="showRootCause"
        :required="actionName === '关闭' && status === '处理中'"
        label="根因"
      >
        <el-select
          v-model="form.rootCause"
          :loading="rootCauseLoading"
          filterable
          clearable
          :placeholder="rootCausePlaceholder"
          style="width: 100%"
        >
          <el-option
            v-for="o in rootCauseOptions"
            :key="o.dictKey"
            :value="o.dictKey"
            :label="o.dictValue"
          />
        </el-select>
        <div v-if="!rootCauseLoading && rootCauseOptions.length === 0" class="dict-empty-hint">
          根因字典暂无启用项，请在 系统管理 → 数据字典（wo_root_cause）维护
        </div>
      </el-form-item>
      <el-form-item
        v-if="actionName === '完成工单' && (woType === '故障维修' || woType === '部件更换')"
        label="更换部件"
      >
        <template v-if="hasReplacementDetails">
          <div class="rep-summary">
            <div v-for="(line, i) in replacementSummary" :key="i">{{ line }}</div>
          </div>
          <div class="rep-summary-hint">
            {{ replacementSummary.length }} 条换件明细（在详情页维护）；确认完成后旧件按明细换下、新件挂载
          </div>
        </template>
        <el-input
          v-else
          v-model="form.replacedAsset"
          placeholder="被更换的旧部件编号（选填，完成时落库）"
        />
      </el-form-item>
      <!-- 维修费用：完成时顺带登记，事后也可在详情页经「维修费用」修正 -->
      <template v-if="showRepairFields">
        <el-divider content-position="left">维修费用（元）</el-divider>
        <el-form-item label="人工费">
          <el-input-number
            v-model="form.costLabor"
            :min="0"
            :max="MAX_COST"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="外委费">
          <el-input-number
            v-model="form.costService"
            :min="0"
            :max="MAX_COST"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="其他费">
          <el-input-number
            v-model="form.costOther"
            :min="0"
            :max="MAX_COST"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <div class="dict-empty-hint">备件费按出库流水自动反算，无需登记</div>
      </template>
      <el-form-item v-if="actionName === '完成工单'" label="完成说明">
        <el-input
          v-model="form.completeNote"
          type="textarea"
          :rows="3"
          placeholder="完成说明（选填，落 complete_note，详情可见）"
        />
      </el-form-item>
      <el-form-item label="意见" :required="actionName === '驳回'">
        <el-input
          v-model="form.opinion"
          type="textarea"
          :rows="3"
          :placeholder="actionName === '驳回' ? '驳回意见（必填）' : '备注 / 审批意见（选填）'"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">确认</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
/* 明细只读汇总（多件换件） */
.rep-summary {
  width: 100%;
  padding: 6px 10px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  word-break: break-all;
}

.rep-summary-hint {
  width: 100%;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

/* 字典项为空/加载中的提示，以及费用口径说明 */
.dict-empty-hint {
  width: 100%;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
