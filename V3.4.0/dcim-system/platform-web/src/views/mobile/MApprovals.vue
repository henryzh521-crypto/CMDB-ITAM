<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addApprovers,
  fetchApproverOptions,
  fetchWorkorders,
  fetchWorkorderDetail,
  transferApprover,
  transitionWorkorder,
} from '@/api/workorder'
import type { WorkorderItem, WorkorderDetail } from '@/api/workorder'

const loading = ref(false)
const loadingMore = ref(false)
const page = ref(1)
const size = 10
const total = ref(0)
const list = ref<WorkorderItem[]>([])

/* ---------------- 详情抽屉 ---------------- */
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<WorkorderDetail | null>(null)
const currentWoNo = ref('')

/** 移动端仅暴露审批相关动作 */
const approvalActions = ref<string[]>([])

/* ---------------- 意见弹窗 ---------------- */
const actionVisible = ref(false)
const actionName = ref('')
const opinion = ref('')
const actionSaving = ref(false)

/* ---------------- 转办 / 加签 ---------------- */
const opMode = ref<'transfer' | 'add'>('transfer')
const opVisible = ref(false)
const opUsernames = ref<string[]>([])
const opOpinion = ref('')
const opSaving = ref(false)
const approverPool = ref<Array<{ username: string; name?: string }>>([])
const approverLoading = ref(false)
const approverLabels = reactive<Record<string, string>>({})

const approverOptions = computed(() => {
  const map = new Map<string, { username: string; name?: string }>()
  for (const u of approverPool.value) {
    map.set(u.username, u)
    if (u.name) approverLabels[u.username] = u.name
  }
  for (const u of opUsernames.value) {
    if (!map.has(u)) map.set(u, { username: u, name: approverLabels[u] })
  }
  return [...map.values()]
})

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

function openOp(mode: 'transfer' | 'add') {
  opMode.value = mode
  opUsernames.value = []
  opOpinion.value = ''
  opVisible.value = true
  void searchApprovers('')
}

const opValid = computed(() =>
  opMode.value === 'transfer'
    ? opUsernames.value.length === 1 && !!opOpinion.value.trim()
    : opUsernames.value.length >= 1,
)

async function submitOp() {
  if (!opValid.value) return
  opSaving.value = true
  try {
    if (opMode.value === 'transfer') {
      await transferApprover(currentWoNo.value, {
        username: opUsernames.value[0],
        opinion: opOpinion.value.trim(),
      })
      ElMessage.success('已转办本级审批人')
    } else {
      await addApprovers(currentWoNo.value, {
        usernames: [...opUsernames.value],
        opinion: opOpinion.value.trim() || undefined,
      })
      ElMessage.success('已加签审批人')
    }
    opVisible.value = false
    if (detail.value) {
      const seq = ++detailSeq
      const woNo = currentWoNo.value
      const res = await fetchWorkorderDetail(woNo)
      if (seq === detailSeq) detail.value = res
    }
  } catch {
    // 拦截器已提示
  } finally {
    opSaving.value = false
  }
}

/** 详情里一行审批信息：当前可批人 + 委托 */
const approvalLineText = computed(() => {
  const d = detail.value
  if (!d) return ''
  const parts: string[] = []
  if (d.currentLevelName) parts.push(`当前：${d.currentLevelName}`)
  if (d.delegate) {
    const names = d.delegate.memberNames?.length ? d.delegate.memberNames : d.delegate.members
    parts.push(
      `${d.delegate.modeLabel || (d.delegate.mode === 'transfer' ? '转办' : '加签')}给 ${names.join('、')}`,
    )
  }
  return parts.join('；')
})

function statusTagType(status?: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (status === '已完成') return 'success'
  if (status === '已驳回') return 'danger'
  if (status === '处理中') return 'primary'
  if (status === '待审批') return 'warning'
  return 'info'
}

async function load(reset: boolean) {
  loading.value = reset
  if (reset) page.value = 1
  try {
    const res = await fetchWorkorders({ page: page.value, size, tab: 'approve' })
    list.value = reset ? res.records ?? [] : [...list.value, ...(res.records ?? [])]
    total.value = res.total ?? 0
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function loadMore() {
  if (list.value.length >= total.value) return
  loadingMore.value = true
  page.value += 1
  void load(false)
}

/** 单调递增的详情请求序号：快速切换工单时丢弃过期响应 */
let detailSeq = 0

async function openDetail(row: WorkorderItem) {
  currentWoNo.value = row.woNo
  detail.value = row as WorkorderDetail
  approvalActions.value = []
  drawerVisible.value = true
  const woNo = row.woNo
  const seq = ++detailSeq
  detailLoading.value = true
  try {
    const res = await fetchWorkorderDetail(woNo)
    if (seq !== detailSeq) return
    detail.value = res
    // 动作即权限：优先用按登录者角色过滤后的 myActions（visible=true 才给按钮），
    // 后端未返回时才回退到全量 actions。
    const source =
      res.myActions && res.myActions.length
        ? res.myActions.filter((m) => m.visible !== false).map((m) => m.action)
        : res.actions || []
    approvalActions.value = source.filter((a) => a === '审批通过' || a === '驳回')
  } catch {
    // 拦截器已提示
  } finally {
    if (seq === detailSeq) detailLoading.value = false
  }
}

function openAction(action: '审批通过' | '驳回') {
  actionName.value = action
  opinion.value = ''
  actionVisible.value = true
}

async function submitAction() {
  const text = opinion.value.trim()
  if (actionName.value === '驳回' && !text) {
    ElMessage.warning('驳回必须填写审批意见')
    return
  }
  try {
    await ElMessageBox.confirm(`确认执行「${actionName.value}」？`, '审批确认', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  actionSaving.value = true
  try {
    await transitionWorkorder(currentWoNo.value, {
      action: actionName.value,
      opinion: text || undefined,
    })
    ElMessage.success(`已执行「${actionName.value}」`)
    actionVisible.value = false
    drawerVisible.value = false
    void load(true)
  } catch {
    // 拦截器已提示（409 非法流转 / 403 无权限）
  } finally {
    actionSaving.value = false
  }
}

onMounted(() => {
  void load(true)
})
</script>

<template>
  <div class="m-appr">
    <div v-loading="loading" class="m-appr-list">
      <el-empty
        v-if="!loading && list.length === 0"
        description="暂无待我审批的工单"
      />

      <div
        v-for="wo in list"
        :key="wo.woNo"
        class="m-card"
        @click="openDetail(wo)"
      >
        <div class="m-card-head">
          <span class="m-card-name">{{ wo.title }}</span>
          <el-tag size="small" :type="statusTagType(wo.status)">{{ wo.status }}</el-tag>
        </div>
        <div class="m-card-row">
          <span class="m-card-label">工单号</span>
          <span class="m-card-value m-mono">{{ wo.woNo }}</span>
        </div>
        <div class="m-card-row">
          <span class="m-card-label">类型</span>
          <span class="m-card-value">{{ wo.woType }}</span>
          <el-tag
            v-if="wo.priority === '高'"
            class="m-priority"
            size="small"
            type="danger"
            effect="plain"
          >
            高优先级
          </el-tag>
        </div>
        <div class="m-card-row">
          <span class="m-card-label">申请人</span>
          <span class="m-card-value">{{ wo.applicant || wo.applicantUsername || '-' }}</span>
        </div>
        <div class="m-card-row">
          <span class="m-card-label">申请时间</span>
          <span class="m-card-value">{{ wo.createdAt || '-' }}</span>
        </div>
      </div>

      <div v-if="list.length < total" class="m-load-more">
        <el-button text type="primary" :loading="loadingMore" @click="loadMore">
          加载更多（{{ list.length }}/{{ total }}）
        </el-button>
      </div>
    </div>

    <!-- 详情抽屉（移动端底部弹出） -->
    <el-drawer
      v-model="drawerVisible"
      title="工单详情"
      direction="btt"
      size="86%"
      :append-to-body="true"
    >
      <div v-loading="detailLoading" class="m-detail">
        <template v-if="detail">
          <div class="m-detail-head">
            <span class="m-detail-title">{{ detail.title }}</span>
            <el-tag size="small" :type="statusTagType(detail.status)">{{ detail.status }}</el-tag>
          </div>
          <div class="m-card-row">
            <span class="m-card-label">工单号</span>
            <span class="m-card-value m-mono">{{ detail.woNo }}</span>
          </div>
          <div class="m-card-row">
            <span class="m-card-label">类型</span>
            <span class="m-card-value">{{ detail.woType }}</span>
          </div>
          <div class="m-card-row">
            <span class="m-card-label">优先级</span>
            <span class="m-card-value">{{ detail.priority || '-' }}</span>
          </div>
          <div class="m-card-row">
            <span class="m-card-label">申请人</span>
            <span class="m-card-value">{{ detail.applicant || detail.applicantUsername || '-' }}</span>
          </div>
          <div class="m-card-row">
            <span class="m-card-label">申请时间</span>
            <span class="m-card-value">{{ detail.createdAt || '-' }}</span>
          </div>
          <div v-if="detail.relatedAssets" class="m-card-row">
            <span class="m-card-label">关联资产</span>
            <span class="m-card-value m-mono">{{ detail.relatedAssets }}</span>
          </div>
          <div v-if="detail.description" class="m-desc">
            <div class="m-card-label">描述</div>
            <div class="m-desc-text">{{ detail.description }}</div>
          </div>

          <div v-if="approvalLineText" class="m-card-row">
            <span class="m-card-label">审批</span>
            <span class="m-card-value">{{ approvalLineText }}</span>
          </div>

          <div v-if="detail.flows?.length" class="m-flows">
            <div class="m-section-title">流转记录</div>
            <div v-for="flow in [...detail.flows].sort((a, b) => (b.seq ?? 0) - (a.seq ?? 0))" :key="flow.seq" class="m-flow-row">
              <span class="m-flow-action">{{ flow.action }}</span>
              <span class="m-flow-meta">
                {{ flow.actor || '-' }} · {{ flow.createdAt || '' }}
              </span>
            </div>
          </div>

          <div v-if="approvalActions.length" class="m-approve-actions">
            <el-button
              v-if="approvalActions.includes('驳回')"
              type="danger"
              size="large"
              plain
              @click="openAction('驳回')"
            >
              驳回
            </el-button>
            <el-button
              v-if="approvalActions.includes('审批通过')"
              type="success"
              size="large"
              @click="openAction('审批通过')"
            >
              通过
            </el-button>
          </div>

          <div v-if="detail.perm?.transfer || detail.perm?.addApprover" class="m-approve-actions m-approve-extra">
            <el-button
              v-if="detail.perm?.transfer"
              plain
              size="small"
              @click="openOp('transfer')"
            >
              转办
            </el-button>
            <el-button
              v-if="detail.perm?.addApprover"
              plain
              size="small"
              @click="openOp('add')"
            >
              加签
            </el-button>
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- 转办 / 加签 -->
    <el-dialog
      v-model="opVisible"
      :title="opMode === 'transfer' ? '转办本级审批人' : '加签审批人'"
      width="90%"
      :close-on-click-modal="false"
    >
      <div class="m-card-label" style="margin-bottom: 4px">
        {{ opMode === 'transfer' ? '转办给（必选一人）' : '加签人（可多选）' }}
      </div>
      <el-select
        v-model="opUsernames"
        :multiple="opMode === 'add'"
        filterable
        remote
        clearable
        :remote-method="searchApprovers"
        :loading="approverLoading"
        :placeholder="opMode === 'transfer' ? '搜索用户名/姓名' : '搜索并选择审批人'"
        style="width: 100%; margin-bottom: 12px"
        @visible-change="(v: boolean) => v && searchApprovers('')"
      >
        <el-option
          v-for="u in approverOptions"
          :key="u.username"
          :label="`${u.name || u.username}（${u.username}）`"
          :value="u.username"
        />
      </el-select>
      <div class="m-card-label" style="margin-bottom: 4px">
        说明{{ opMode === 'transfer' ? '（必填）' : '（选填）' }}
      </div>
      <el-input
        v-model="opOpinion"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        :placeholder="opMode === 'transfer' ? '写入流转记录并通知对方' : '加签原因'"
      />
      <template #footer>
        <el-button :disabled="opSaving" @click="opVisible = false">取消</el-button>
        <el-button type="primary" :loading="opSaving" :disabled="!opValid" @click="submitOp">
          确认
        </el-button>
      </template>
    </el-dialog>

    <!-- 审批意见 -->
    <el-dialog
      v-model="actionVisible"
      :title="actionName === '驳回' ? '驳回意见' : '通过意见'"
      width="90%"
      :close-on-click-modal="false"
    >
      <el-input
        v-model="opinion"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        :placeholder="actionName === '驳回' ? '请填写驳回意见（必填）' : '审批意见（选填）'"
      />
      <template #footer>
        <el-button :disabled="actionSaving" @click="actionVisible = false">取消</el-button>
        <el-button
          :type="actionName === '驳回' ? 'danger' : 'success'"
          :loading="actionSaving"
          @click="submitAction"
        >
          确认
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.m-appr {
  padding: 12px;
}

.m-card {
  background: var(--el-bg-color);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 10px;
  cursor: pointer;
}

.m-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.m-card-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.m-card-row {
  display: flex;
  align-items: center;
  font-size: 13px;
  line-height: 22px;
}

.m-card-label {
  width: 64px;
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

.m-priority {
  margin-left: 8px;
}

.m-load-more {
  text-align: center;
  padding: 4px 0 12px;
}

.m-detail {
  min-height: 200px;
}

.m-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.m-detail-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.m-desc {
  margin-top: 8px;
  font-size: 13px;
}

.m-desc-text {
  margin-top: 4px;
  color: var(--el-text-color-primary);
  line-height: 20px;
  white-space: pre-wrap;
  word-break: break-all;
}

.m-flows {
  margin-top: 16px;
}

.m-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 6px;
}

.m-flow-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-regular);
  line-height: 24px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.m-flow-action {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.m-approve-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.m-approve-actions .el-button {
  flex: 1;
}

.m-approve-extra {
  margin-top: 10px;
}
</style>
