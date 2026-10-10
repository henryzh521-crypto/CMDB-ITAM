<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/client'
import type { PageResult, RoleItem } from '@/api/types'
import {
  fetchApproverOptions,
  fetchSlaConfig,
  fetchWorkflowConfig,
  updateSlaConfig,
  updateWorkflowConfig,
} from '@/api/workorder'
import type { SlaConfigItem, WorkflowConfigItem } from '@/api/workorder'

/**
 * 工单配置页（仅管理员可写）：
 *   · 工作流配置 —— 是否需审批 + 按级审批角色链 + 按级指定审批人（与角色取并集）
 *   · SLA 配置   —— 类型 × 优先级 的响应/解决时限与升级角色
 * 两端均为 PUT，后端以「仅管理员可修改工作流/SLA配置」403 兜底。
 */
const router = useRouter()
const activeTab = ref('workflow')

const workflowRows = ref<WorkflowConfigItem[]>([])
const workflowLoading = ref(false)
const slaRows = ref<SlaConfigItem[]>([])
const slaLoading = ref(false)
const roleOptions = ref<RoleItem[]>([])
const savingKey = ref('')

async function loadRoles() {
  try {
    const res = await http.get<any, PageResult<RoleItem>>('/roles', {
      params: { page: 1, size: 200 },
    })
    roleOptions.value = res.records ?? []
  } catch {
    // 拦截器已提示
  }
}

async function loadWorkflow() {
  workflowLoading.value = true
  try {
    workflowRows.value = await fetchWorkflowConfig()
  } catch {
    // 拦截器已提示（非管理员 403）
  } finally {
    workflowLoading.value = false
  }
}

async function loadSla() {
  slaLoading.value = true
  try {
    slaRows.value = await fetchSlaConfig()
  } catch {
    // 拦截器已提示
  } finally {
    slaLoading.value = false
  }
}

function roleName(code: string | null | undefined): string {
  if (!code) return '-'
  const hit = roleOptions.value.find((r) => r.roleCode === code)
  return hit ? `${hit.name}（${code}）` : code
}

/* ---------------- 工作流配置 ---------------- */

interface WorkflowDraft {
  needApproval: boolean
  approvalRoles: string[]
  /** 按级指定审批人（下标对齐 approvalRoles；与角色取并集） */
  approvalUserLevels: string[][]
}
const workflowDrafts = reactive<Record<string, WorkflowDraft>>({})

/* ---------------- 审批人选择器数据源 ---------------- */

interface ApproverOption {
  username: string
  name?: string
}
const approverPool = ref<ApproverOption[]>([])
const approverLoading = ref(false)
const approverLabels = reactive<Record<string, string>>({})

async function searchApprovers(q: string) {
  approverLoading.value = true
  try {
    approverPool.value = await fetchApproverOptions(q || undefined)
  } catch {
    // 拦截器已提示
  } finally {
    approverLoading.value = false
  }
}

/** 下拉选项 = 搜索池 ∪ 已选中但不在池里的人（保证已选项显示中文名） */
function approverOptionsFor(selected: string[] | undefined): ApproverOption[] {
  const map = new Map<string, ApproverOption>()
  for (const u of approverPool.value) {
    map.set(u.username, u)
    if (u.name) approverLabels[u.username] = u.name
  }
  for (const u of selected ?? []) {
    if (!map.has(u)) map.set(u, { username: u, name: approverLabels[u] })
  }
  return [...map.values()]
}

function syncWorkflowDrafts() {
  for (const row of workflowRows.value) {
    const roles = [...(row.approvalRoles ?? [])]
    const levels: string[][] = (row.approvalUserLevels ?? []).map((l) => [...l])
    while (levels.length < roles.length) levels.push([])
    workflowDrafts[row.woType] = {
      needApproval: row.needApproval,
      approvalRoles: roles,
      approvalUserLevels: levels,
    }
  }
}

/** 展示级数 = 角色级数与指定人级数的较大者（后端 chainLen 同口径） */
function levelCount(woType: string): number {
  const d = workflowDrafts[woType]
  if (!d) return 0
  return Math.max(d.approvalRoles.length, d.approvalUserLevels.length)
}

/** 角色链增删后补齐指定人级数（只增不减，避免丢掉仅配置指定人的级别） */
function onRolesChange(woType: string) {
  const d = workflowDrafts[woType]
  if (!d) return
  while (d.approvalUserLevels.length < d.approvalRoles.length) d.approvalUserLevels.push([])
}

function dirtyWorkflow(woType: string): boolean {
  const row = workflowRows.value.find((r) => r.woType === woType)
  const d = workflowDrafts[woType]
  if (!row || !d) return false
  if (d.needApproval !== row.needApproval) return true
  if (d.approvalRoles.join(',') !== (row.approvalRoles ?? []).join(',')) return true
  const cur = (d.approvalUserLevels ?? []).map((l) => [...l].sort().join(','))
  const saved = (row.approvalUserLevels ?? []).map((l) => [...l].sort().join(','))
  return JSON.stringify(cur) !== JSON.stringify(saved)
}

async function saveWorkflow(woType: string) {
  const d = workflowDrafts[woType]
  if (!d) return
  const chainLen = levelCount(woType)
  const hasUserLevels = d.approvalUserLevels.some((l) => l.length > 0)
  if (d.needApproval && !d.approvalRoles.length && !hasUserLevels) {
    ElMessage.warning('勾选「需审批」时至少要选择一个审批角色或指定审批人')
    return
  }
  if (chainLen > 5) {
    ElMessage.warning('审批链最多 5 级')
    return
  }
  savingKey.value = `wf:${woType}`
  try {
    const updated = await updateWorkflowConfig(woType, {
      needApproval: d.needApproval,
      approvalRoles: [...d.approvalRoles],
      approvalUserLevels: d.approvalUserLevels.map((l) => [...l]),
    })
    const i = workflowRows.value.findIndex((r) => r.woType === woType)
    if (i >= 0) workflowRows.value[i] = updated
    syncWorkflowDrafts()
    ElMessage.success(`已保存「${woType}」审批链配置`)
  } catch {
    // 拦截器已提示（403 仅管理员 / 400 角色或用户无效）
  } finally {
    savingKey.value = ''
  }
}

/* ---------------- SLA 配置 ---------------- */

interface SlaDraft {
  responseMinutes?: number
  resolveMinutes?: number
  escalateTo?: string
}
const slaDrafts = reactive<Record<string, SlaDraft>>({})

function slaKey(woType: string, priority: string) {
  return `${woType}|${priority}`
}

function syncSlaDrafts() {
  for (const row of slaRows.value) {
    slaDrafts[slaKey(row.woType, row.priority)] = {
      responseMinutes: row.responseMinutes ?? undefined,
      resolveMinutes: row.resolveMinutes ?? undefined,
      escalateTo: row.escalateTo ?? undefined,
    }
  }
}

function dirtySla(row: SlaConfigItem): boolean {
  const d = slaDrafts[slaKey(row.woType, row.priority)]
  if (!d) return false
  return (
    (d.responseMinutes ?? undefined) !== (row.responseMinutes ?? undefined) ||
    (d.resolveMinutes ?? undefined) !== (row.resolveMinutes ?? undefined) ||
    (d.escalateTo ?? undefined) !== (row.escalateTo ?? undefined)
  )
}

async function saveSla(row: SlaConfigItem) {
  const d = slaDrafts[slaKey(row.woType, row.priority)]
  if (!d) return
  const resp = d.responseMinutes
  const res = d.resolveMinutes
  if (!resp || !res) {
    ElMessage.warning('响应时限与解决时限都必须为正整数（分钟）')
    return
  }
  if (res < resp) {
    ElMessage.warning('解决时限不能早于响应时限')
    return
  }
  savingKey.value = `sla:${row.woType}|${row.priority}`
  try {
    const updated = await updateSlaConfig(row.woType, row.priority, {
      responseMinutes: resp,
      resolveMinutes: res,
      escalateTo: d.escalateTo || undefined,
    })
    const i = slaRows.value.findIndex(
      (r) => r.woType === row.woType && r.priority === row.priority,
    )
    if (i >= 0) slaRows.value[i] = updated
    syncSlaDrafts()
    ElMessage.success(`已保存「${row.woType} / ${row.priority}」SLA`)
  } catch {
    // 拦截器已提示
  } finally {
    savingKey.value = ''
  }
}

function back() {
  void router.push('/workorders')
}

onMounted(() => {
  void loadRoles()
  void loadWorkflow().then(syncWorkflowDrafts)
  void loadSla().then(syncSlaDrafts)
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">
      工单配置
      <span class="page-title-side">
        <el-button size="small" plain @click="back">← 返回工单列表</el-button>
      </span>
    </div>
    <div class="page-hint">
      本页改动仅管理员可提交；后端对工作流 / SLA 配置均返回 403 兜底，非管理员无法绕过。
    </div>

    <el-tabs v-model="activeTab">
      <!-- 工作流配置 -->
      <el-tab-pane label="工作流（审批链）" name="workflow">
        <el-table v-loading="workflowLoading" :data="workflowRows" border stripe>
          <el-table-column prop="woType" label="工单类型" width="140" />
          <el-table-column label="需审批" width="120">
            <template #default="{ row }">
              <el-switch
                v-if="workflowDrafts[row.woType]"
                v-model="workflowDrafts[row.woType].needApproval"
                active-text="是"
                inactive-text="否"
              />
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="审批链（按级别）" min-width="320">
            <template #default="{ row }">
              <template v-if="workflowDrafts[row.woType]?.needApproval">
                <el-select
                  v-model="workflowDrafts[row.woType].approvalRoles"
                  multiple
                  filterable
                  clearable
                  placeholder="选择审批角色（数组序 = 级别序）"
                  style="width: 100%"
                  @change="onRolesChange(row.woType)"
                >
                  <el-option
                    v-for="r in roleOptions"
                    :key="r.roleCode"
                    :label="`${r.name}（${r.roleCode}）`"
                    :value="r.roleCode"
                  />
                </el-select>
                <div
                  v-for="idx in levelCount(row.woType)"
                  :key="idx - 1"
                  class="level-user-row"
                >
                  <span class="level-user-label">第 {{ idx }} 级指定人</span>
                  <el-select
                    v-model="workflowDrafts[row.woType].approvalUserLevels[idx - 1]"
                    multiple
                    filterable
                    remote
                    clearable
                    :remote-method="searchApprovers"
                    :loading="approverLoading"
                    placeholder="可选；与该级角色取并集（或签）"
                    size="small"
                    style="flex: 1"
                    @visible-change="(v: boolean) => v && searchApprovers('')"
                  >
                    <el-option
                      v-for="u in approverOptionsFor(
                        workflowDrafts[row.woType].approvalUserLevels[idx - 1],
                      )"
                      :key="u.username"
                      :label="`${u.name || u.username}（${u.username}）`"
                      :value="u.username"
                    />
                  </el-select>
                </div>
              </template>
              <span v-else class="muted">免审（动作里不含「提交审批」）</span>
            </template>
          </el-table-column>
          <el-table-column label="当前已存审批链" min-width="200">
            <template #default="{ row }">
              <template v-if="row.approvalRoleNames?.length">
                {{ row.approvalRoleNames.join(' → ') }}
              </template>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button
                type="primary"
                size="small"
                :disabled="!dirtyWorkflow(row.woType)"
                :loading="savingKey === `wf:${row.woType}`"
                @click="saveWorkflow(row.woType)"
              >
                保存
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- SLA 配置 -->
      <el-tab-pane label="SLA（时限矩阵）" name="sla">
        <div class="page-hint">
          行 = 工单类型，列 = 优先级；单位为分钟。空值表示该组合未配置，后端会按需补默认。
        </div>
        <el-table v-loading="slaLoading" :data="slaRows" border stripe>
          <el-table-column prop="woType" label="工单类型" width="130" />
          <el-table-column label="优先级" width="90">
            <template #default="{ row }">
              <el-tag :type="row.priority === '高' ? 'danger' : row.priority === '中' ? 'warning' : 'info'" size="small">
                {{ row.priority }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="响应时限（分钟）" width="190">
            <template #default="{ row }">
              <el-input-number
                v-if="slaDrafts[slaKey(row.woType, row.priority)]"
                v-model="slaDrafts[slaKey(row.woType, row.priority)].responseMinutes"
                :min="1"
                :max="100000"
                controls-position="right"
                size="small"
                style="width: 150px"
              />
            </template>
          </el-table-column>
          <el-table-column label="解决时限（分钟）" width="190">
            <template #default="{ row }">
              <el-input-number
                v-if="slaDrafts[slaKey(row.woType, row.priority)]"
                v-model="slaDrafts[slaKey(row.woType, row.priority)].resolveMinutes"
                :min="1"
                :max="100000"
                controls-position="right"
                size="small"
                style="width: 150px"
              />
            </template>
          </el-table-column>
          <el-table-column label="超期升级通知角色" min-width="220">
            <template #default="{ row }">
              <el-select
                v-if="slaDrafts[slaKey(row.woType, row.priority)]"
                v-model="slaDrafts[slaKey(row.woType, row.priority)].escalateTo"
                clearable
                filterable
                placeholder="不升级"
                size="small"
                style="width: 100%"
              >
                <el-option
                  v-for="r in roleOptions"
                  :key="r.roleCode"
                  :label="`${r.name}（${r.roleCode}）`"
                  :value="r.roleCode"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="已存值" min-width="200">
            <template #default="{ row }">
              <span v-if="row.configured" class="muted">
                {{ row.responseMinutes ?? '-' }} / {{ row.resolveMinutes ?? '-' }} 分钟
                ｜ 升级 {{ roleName(row.escalateTo) }}
              </span>
              <span v-else class="muted">未配置</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button
                type="primary"
                size="small"
                :disabled="!dirtySla(row)"
                :loading="savingKey === `sla:${slaKey(row.woType, row.priority)}`"
                @click="saveSla(row)"
              >
                保存
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page-title-side {
  float: right;
}

.page-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  margin: 0 0 12px;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.level-user-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.level-user-label {
  flex: 0 0 auto;
  width: 82px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: right;
}
</style>
