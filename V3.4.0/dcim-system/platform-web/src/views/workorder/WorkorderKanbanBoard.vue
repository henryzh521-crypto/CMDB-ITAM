<script setup lang="ts">
/**
 * v3.5 工单看板：按状态分列，原生 HTML5 DnD 拖拽流转。
 * - 拖拽卡片到目标列 → 调 transition 接口；失败（409 非法流转 / 403 无权限）卡片留在原列（回弹）
 * - 驳回需必填意见（弹窗输入）；处理中→已关闭需根因分类，看板不提供（引导详情页）
 * - 与列表页共享筛选条件（props.filters）；数据最多 200 条（后端分页上限）
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchWorkorders, transitionWorkorder, WO_STATUSES } from '@/api/workorder'
import type { WorkorderItem } from '@/api/workorder'
import { isSlaOverdue, priorityTag, statusTag } from './wo-shared'

const props = defineProps<{
  /** 列表页当前筛选条件（status 之外的条件同样生效） */
  filters: Record<string, unknown>
}>()
const emit = defineEmits<{ (e: 'open', wo: WorkorderItem): void }>()

/** 看板拖拽可达的状态迁移表（与后端 WorkorderStateMachine 对齐；
 *  处理中→已关闭需根因分类且仅管理员，看板不提供，引导到详情页） */
const DRAG_ACTIONS: Record<string, Record<string, string>> = {
  待处理: { 处理中: '开始处理', 待审批: '提交审批' },
  待审批: { 处理中: '审批通过', 已驳回: '驳回' },
  已驳回: { 待审批: '重新提交', 已关闭: '关闭' },
  处理中: { 已完成: '完成工单', 待处理: '退回' },
  已完成: { 处理中: '重新打开' },
}

const columns = WO_STATUSES as readonly string[]
const records = ref<WorkorderItem[]>([])
const total = ref(0)
const loading = ref(false)
/** 流转请求进行中的工单（防重复提交） */
const busyWo = ref('')
const dragFrom = ref<{ woNo: string; from: string } | null>(null)
const dragOverCol = ref('')

const byStatus = computed(() => {
  const m: Record<string, WorkorderItem[]> = {}
  columns.forEach((s) => (m[s] = []))
  records.value.forEach((r) => {
    if (m[r.status]) m[r.status].push(r)
  })
  return m
})

const truncated = computed(() => total.value > records.value.length)

async function load() {
  loading.value = true
  try {
    const res = await fetchWorkorders({ ...props.filters, page: 1, size: 200 })
    records.value = res.records ?? []
    total.value = res.total ?? 0
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

/** 该状态的卡片是否可拖（有可达迁移才可拖） */
function draggableOf(status: string): boolean {
  return Object.keys(DRAG_ACTIONS[status] ?? {}).length > 0
}

function onDragStart(e: DragEvent, wo: WorkorderItem) {
  if (!draggableOf(wo.status) || busyWo.value === wo.woNo) {
    e.preventDefault()
    return
  }
  dragFrom.value = { woNo: wo.woNo, from: wo.status }
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', wo.woNo)
  }
}

function onDragOver(e: DragEvent, status: string) {
  if (!dragFrom.value) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  dragOverCol.value = status
}

function onDrop(e: DragEvent, status: string) {
  e.preventDefault()
  const d = dragFrom.value
  dragFrom.value = null
  dragOverCol.value = ''
  if (!d) return
  void doTransition(d, status)
}

function onDragEnd() {
  dragFrom.value = null
  dragOverCol.value = ''
}

async function doTransition(d: { woNo: string; from: string }, to: string) {
  if (d.from === to) return
  const action = DRAG_ACTIONS[d.from]?.[to]
  if (!action) {
    if (d.from === '处理中' && to === '已关闭') {
      ElMessage.warning('处理中工单请从详情页关闭（需选择根因分类）')
    } else {
      ElMessage.info(`「${d.from}」工单不支持直接移动到「${to}」`)
    }
    return
  }
  let opinion: string | undefined
  if (action === '驳回') {
    try {
      const { value } = await ElMessageBox.prompt('驳回必须填写审批意见', `驳回 ${d.woNo}`, {
        inputPlaceholder: '驳回意见（必填）',
        inputValidator: (v: string) => !!v?.trim() || '请填写驳回意见',
      })
      opinion = value.trim()
    } catch {
      return // 取消 = 卡片回弹
    }
  }
  busyWo.value = d.woNo
  try {
    await transitionWorkorder(d.woNo, { action, opinion })
    ElMessage.success(`已执行「${action}」`)
    await load()
  } catch {
    // 拦截器已提示（409 非法流转 / 403 无权限）→ 卡片留在原列（回弹）
  } finally {
    busyWo.value = ''
  }
}

function fmtTime(v?: string): string {
  return v ? v.replace('T', ' ').slice(5, 16) : '-'
}

/** 筛选条件变化（查询/重置/快捷开关）时看板跟随刷新 */
watch(() => props.filters, () => void load())

onMounted(() => void load())
</script>

<template>
  <div v-loading="loading" class="kanban">
    <div v-if="truncated" class="kanban-hint">
      <el-tag type="info" size="small">仅显示前 {{ records.length }} 条（共 {{ total }} 条），可用筛选缩小范围</el-tag>
    </div>
    <div class="kanban-cols">
      <div
        v-for="col in columns"
        :key="col"
        class="kb-col"
        :class="{ 'kb-over': dragOverCol === col && !!dragFrom }"
        @dragover="onDragOver($event, col)"
        @dragleave="dragOverCol = ''"
        @drop="onDrop($event, col)"
      >
        <div class="kb-head">
          <el-tag :type="statusTag(col)" effect="plain">{{ col }}</el-tag>
          <span class="kb-count">{{ byStatus[col].length }}</span>
        </div>
        <div class="kb-body">
          <div
            v-for="wo in byStatus[col]"
            :key="wo.woNo"
            class="kb-card"
            :class="{
              'kb-draggable': draggableOf(col),
              'kb-busy': busyWo === wo.woNo,
              'kb-dragging': dragFrom?.woNo === wo.woNo,
            }"
            :draggable="draggableOf(col) && busyWo !== wo.woNo"
            :title="draggableOf(col) ? '拖拽到其他列流转状态，点击打开详情' : '终态，点击打开详情'"
            @dragstart="onDragStart($event, wo)"
            @dragend="onDragEnd"
            @click="emit('open', wo)"
          >
            <div class="kb-card-top">
              <span class="kb-no">{{ wo.woNo }}</span>
              <el-tag v-if="wo.priority" :type="priorityTag(wo.priority)" size="small">{{ wo.priority }}</el-tag>
              <el-tag v-if="isSlaOverdue(wo)" type="danger" size="small" effect="dark">超期</el-tag>
            </div>
            <div class="kb-title">{{ wo.title }}</div>
            <div class="kb-meta">
              <span>{{ wo.woType }}</span>
              <span>{{ wo.handler ? wo.handler : wo.applicant || '-' }}</span>
              <span>{{ fmtTime(wo.applyTime || wo.createdAt) }}</span>
            </div>
          </div>
          <div v-if="!byStatus[col].length" class="kb-empty">暂无工单</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kanban-hint {
  margin-bottom: 8px;
}
.kanban-cols {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  align-items: flex-start;
  padding-bottom: 8px;
}
.kb-col {
  flex: 0 0 280px;
  min-height: 320px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  transition: border-color 0.15s;
}
.kb-col.kb-over {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7) inset;
}
.kb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px 6px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.kb-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}
.kb-body {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 260px;
  max-height: 640px;
  overflow-y: auto;
}
.kb-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 5px;
  padding: 8px 10px;
  cursor: pointer;
  background: var(--el-bg-color);
}
.kb-card:hover {
  border-color: var(--el-color-primary-light-5);
}
.kb-card.kb-draggable {
  cursor: grab;
}
.kb-card.kb-dragging {
  opacity: 0.45;
}
.kb-card.kb-busy {
  opacity: 0.6;
  pointer-events: none;
}
.kb-card-top {
  display: flex;
  align-items: center;
  gap: 6px;
}
.kb-no {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  font-variant-numeric: tabular-nums;
}
.kb-title {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  word-break: break-all;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.kb-meta {
  margin-top: 6px;
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  flex-wrap: wrap;
}
.kb-empty {
  border: 1px dashed var(--el-border-color-lighter);
  border-radius: 5px;
  padding: 14px 0;
  text-align: center;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
