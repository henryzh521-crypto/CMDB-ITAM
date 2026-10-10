<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { InputInstance } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  fetchInventoryTaskDetail,
  fetchInventoryTasks,
  fetchInventoryItems,
  updateInventoryItem,
} from '@/api/warehouse'
import type { InventoryTaskItem, InventoryItemDetail } from '@/api/warehouse'

type Mode = 'list' | 'task'

const mode = ref<Mode>('list')
const loading = ref(false)
const tasks = ref<InventoryTaskItem[]>([])

const currentTask = ref<InventoryTaskItem | null>(null)
const items = ref<InventoryItemDetail[]>([])
const itemsLoading = ref(false)
const scanValue = ref('')
const scanInputRef = ref<InputInstance | null>(null)

/* ---------------- 结果弹窗 ---------------- */
const dialogVisible = ref(false)
const matchedItem = ref<InventoryItemDetail | null>(null)
const abnormalVisible = ref(false)
const abnormalResult = ref<'盘亏' | '盘盈' | '错位'>('盘亏')
const diffNote = ref('')
const submitting = ref(false)

const RESULT_LABELS: Record<string, string> = {
  一致: '正常',
  盘亏: '盘亏',
  盘盈: '盘盈',
  错位: '错位',
  未盘: '未盘',
}

function resultTagType(result?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (result === '一致') return 'success'
  if (result === '错位' || result === '盘盈') return 'warning'
  if (result === '盘亏') return 'danger'
  return 'info'
}

const isDone = (item: InventoryItemDetail) => !!item.result && item.result !== '未盘'
const progressPercent = computed(() => {
  const total = currentTask.value?.totalCount ?? 0
  if (!total) return 0
  const checked = currentTask.value?.checkedCount ?? 0
  return Math.min(100, Math.round((checked / total) * 100))
})

/* ---------------- 任务列表 ---------------- */
async function loadTasks() {
  loading.value = true
  try {
    const res = await fetchInventoryTasks({ page: 1, size: 50, status: '盘点中' })
    tasks.value = res.records ?? []
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

/* ---------------- 进入任务 ---------------- */
async function enterTask(task: InventoryTaskItem) {
  mode.value = 'task'
  currentTask.value = task
  items.value = []
  scanValue.value = ''
  await loadTaskData(task.taskNo)
  void nextTick(() => scanInputRef.value?.focus())
}

async function loadTaskData(taskNo: string) {
  itemsLoading.value = true
  try {
    const [detail, allItems] = await Promise.all([
      fetchInventoryTaskDetail(taskNo),
      loadAllItems(taskNo),
    ])
    currentTask.value = detail
    items.value = allItems
  } catch {
    // 拦截器已提示
  } finally {
    itemsLoading.value = false
  }
}

/** 明细全量拉取（分页循环，供扫码前端定位） */
async function loadAllItems(taskNo: string): Promise<InventoryItemDetail[]> {
  const all: InventoryItemDetail[] = []
  const size = 500
  for (let page = 1; page <= 20; page++) {
    const res = await fetchInventoryItems(taskNo, { page, size })
    const records = res.records ?? []
    all.push(...records)
    if (records.length < size) break
  }
  return all
}

function backToList() {
  mode.value = 'list'
  currentTask.value = null
  items.value = []
  void loadTasks()
}

/* ---------------- 扫码定位 ---------------- */
function onScanInput() {
  const value = scanValue.value.trim().toLowerCase()
  if (!value) return
  const hit = items.value.find(
    (it) =>
      (it.assetNo || '').trim().toLowerCase() === value ||
      (it.expected?.sn || '').trim().toLowerCase() === value,
  )
  if (!hit) {
    ElMessage.warning('未在任务明细中找到该资产')
    scanValue.value = ''
    return
  }
  matchedItem.value = hit
  abnormalVisible.value = false
  abnormalResult.value = '盘亏'
  diffNote.value = ''
  dialogVisible.value = true
  scanValue.value = ''
}

function onMatchedClick(item: InventoryItemDetail) {
  matchedItem.value = item
  abnormalVisible.value = false
  abnormalResult.value = '盘亏'
  diffNote.value = ''
  dialogVisible.value = true
}

/* ---------------- 提交结果 ---------------- */
async function submitResult(result: string) {
  const item = matchedItem.value
  if (!item) return
  if (result !== '一致' && !diffNote.value.trim()) {
    ElMessage.warning('异常盘点必须填写备注')
    return
  }
  const wasUndone = !item.result || item.result === '未盘'
  submitting.value = true
  try {
    await updateInventoryItem(currentTask.value!.taskNo, item.itemNo, {
      result,
      diffNote: diffNote.value.trim() || undefined,
    })
    // 本地同步：更新行 + 进度（原未盘时才累加）
    item.result = result
    item.diffNote = diffNote.value.trim() || undefined
    item.checkedAt = new Date().toISOString()
    if (currentTask.value && wasUndone) {
      currentTask.value = {
        ...currentTask.value,
        checkedCount: Math.min(
          currentTask.value.totalCount,
          currentTask.value.checkedCount + 1,
        ),
      }
    }
    ElMessage.success(`已记录：${RESULT_LABELS[result] || result}`)
    dialogVisible.value = false
    matchedItem.value = null
    void nextTick(() => scanInputRef.value?.focus())
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

function submitNormal() {
  abnormalVisible.value = false
  void submitResult('一致')
}

function submitAbnormal() {
  void submitResult(abnormalResult.value)
}

onMounted(() => {
  void loadTasks()
})
</script>

<template>
  <div class="m-inv">
    <!-- 进行中任务列表 -->
    <template v-if="mode === 'list'">
      <div v-loading="loading" class="m-inv-list">
        <el-empty v-if="!loading && tasks.length === 0" description="暂无进行中的盘点任务" />
        <div
          v-for="task in tasks"
          :key="task.taskNo"
          class="m-card"
          @click="enterTask(task)"
        >
          <div class="m-card-head">
            <span class="m-card-name">{{ task.name }}</span>
            <el-tag size="small" type="primary">{{ task.status }}</el-tag>
          </div>
          <div class="m-card-row">
            <span class="m-card-label">任务号</span>
            <span class="m-card-value m-mono">{{ task.taskNo }}</span>
          </div>
          <div class="m-card-row">
            <span class="m-card-label">盘点人</span>
            <span class="m-card-value">{{ task.inspector }}</span>
          </div>
          <div class="m-card-row">
            <span class="m-card-label">进度</span>
            <span class="m-card-value">
              {{ task.checkedCount }}/{{ task.totalCount }}
              <span class="m-diff">（差异 {{ task.diffCount }}）</span>
            </span>
          </div>
          <el-progress
            :percentage="task.totalCount ? Math.min(100, Math.round((task.checkedCount / task.totalCount) * 100)) : 0"
            :stroke-width="6"
          />
        </div>
      </div>
    </template>

    <!-- 任务盘点 -->
    <template v-else>
      <div class="m-inv-task">
        <div class="m-task-bar">
          <el-button text :icon="ArrowLeft" @click="backToList">返回</el-button>
          <span class="m-task-name">{{ currentTask?.name }}</span>
        </div>

        <div class="m-progress-panel">
          <div class="m-progress-text">
            已盘 <b>{{ currentTask?.checkedCount ?? 0 }}</b> / {{ currentTask?.totalCount ?? 0 }}
            <span class="m-diff">（差异 {{ currentTask?.diffCount ?? 0 }}）</span>
          </div>
          <el-progress :percentage="progressPercent" :stroke-width="10" />
        </div>

        <div class="m-scan-bar">
          <el-input
            ref="scanInputRef"
            v-model="scanValue"
            size="large"
            clearable
            placeholder="扫码 / 输入资产编号或 SN，回车定位"
            :disabled="itemsLoading || currentTask?.status !== '盘点中'"
            @keyup.enter="onScanInput"
          />
          <el-button type="primary" size="large" :disabled="itemsLoading" @click="onScanInput">
            定位
          </el-button>
        </div>

        <div v-loading="itemsLoading" class="m-inv-items">
          <el-empty v-if="!itemsLoading && items.length === 0" description="该任务暂无明细" />
          <div
            v-for="item in items"
            :key="String(item.id)"
            class="m-card m-item"
            :class="{ 'is-done': isDone(item) }"
            @click="onMatchedClick(item)"
          >
            <div class="m-card-row">
              <span class="m-card-label">资产</span>
              <span class="m-card-value m-mono">{{ item.assetNo }}</span>
            </div>
            <div class="m-card-row">
              <span class="m-card-label">SN</span>
              <span class="m-card-value m-mono">{{ item.expected?.sn || '-' }}</span>
              <el-tag class="m-item-result" size="small" :type="resultTagType(item.result)">
                {{ RESULT_LABELS[item.result] || item.result || '未盘' }}
              </el-tag>
            </div>
            <div class="m-card-row">
              <span class="m-card-label">位置</span>
              <span class="m-card-value">
                {{ [item.expected?.rackCode, item.expected?.unitNo].filter(Boolean).join(' / ') || '-' }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 盘点结果选择 -->
      <el-dialog
        v-model="dialogVisible"
        title="盘点结果"
        width="90%"
        :close-on-click-modal="false"
      >
        <div v-if="matchedItem" class="m-dialog-body">
          <div class="m-card-row">
            <span class="m-card-label">资产</span>
            <span class="m-card-value m-mono">{{ matchedItem.assetNo }}</span>
          </div>
          <div class="m-card-row">
            <span class="m-card-label">SN</span>
            <span class="m-card-value m-mono">{{ matchedItem.expected?.sn || '-' }}</span>
          </div>
          <div class="m-card-row">
            <span class="m-card-label">当前位置</span>
            <span class="m-card-value">
              {{ [matchedItem.expected?.rackCode, matchedItem.expected?.unitNo].filter(Boolean).join(' / ') || '-' }}
            </span>
          </div>

          <template v-if="!abnormalVisible">
            <div class="m-result-actions">
              <el-button type="success" size="large" :loading="submitting" @click="submitNormal">
                正常
              </el-button>
              <el-button
                type="danger"
                size="large"
                plain
                :disabled="submitting"
                @click="abnormalVisible = true"
              >
                异常
              </el-button>
            </div>
          </template>

          <template v-else>
            <div class="m-abnormal-form">
              <div class="m-abnormal-label">异常类型</div>
              <el-radio-group v-model="abnormalResult">
                <el-radio-button value="盘亏">盘亏</el-radio-button>
                <el-radio-button value="盘盈">盘盈</el-radio-button>
                <el-radio-button value="错位">错位</el-radio-button>
              </el-radio-group>
              <div class="m-abnormal-label">备注（必填）</div>
              <el-input
                v-model="diffNote"
                type="textarea"
                :rows="3"
                maxlength="200"
                show-word-limit
                placeholder="请填写异常说明"
              />
              <div class="m-result-actions">
                <el-button size="large" :disabled="submitting" @click="abnormalVisible = false">
                  返回
                </el-button>
                <el-button type="danger" size="large" :loading="submitting" @click="submitAbnormal">
                  提交异常
                </el-button>
              </div>
            </div>
          </template>
        </div>
      </el-dialog>
    </template>
  </div>
</template>

<style scoped>
.m-inv {
  padding: 12px;
}

.m-card {
  background: var(--el-bg-color);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 10px;
}

.m-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.m-card-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.m-card-row {
  display: flex;
  align-items: center;
  font-size: 13px;
  line-height: 22px;
}

.m-card-label {
  width: 56px;
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}

.m-card-value {
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.m-mono {
  font-family: Consolas, Menlo, monospace;
}

.m-diff {
  color: #e6a23c;
}

.m-task-bar {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.m-task-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-progress-panel {
  background: var(--el-bg-color);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 10px;
}

.m-progress-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 8px;
}

.m-progress-text b {
  color: var(--el-color-primary);
  font-size: 16px;
}

.m-scan-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.m-scan-bar .el-button {
  flex-shrink: 0;
}

.m-item.is-done {
  border-left: 3px solid var(--el-color-success);
  background: #f0f9eb;
}

.m-item-result {
  margin-left: auto;
}

.m-result-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.m-result-actions .el-button {
  flex: 1;
}

.m-abnormal-form {
  padding-top: 4px;
}

.m-abnormal-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin: 12px 0 6px;
}
</style>
