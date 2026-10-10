<script setup lang="ts">
/**
 * 问题管理（ITIL）：从反复发生的故障中找根因、沉淀已知错误、推动永久解决。
 * 问题建议页自动列出「同资产反复报修 / 同资产同告警反复触发 / 同型号多台故障」。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import AssetPicker from '@/components/AssetPicker.vue'
import {
  KB_CATEGORY_LABEL,
  PROBLEM_NEXT,
  PROBLEM_STATUS_LABEL,
  PROBLEM_STATUS_TAG,
  createProblem,
  fetchProblem,
  fetchProblemStats,
  fetchProblemSuggestions,
  fetchProblems,
  linkProblem,
  transitionProblem,
  unlinkProblem,
  updateProblem,
} from '@/api/itsm'
import type { ProblemDetail, ProblemItem, ProblemPayload } from '@/api/itsm'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const canWrite = computed(() => auth.canWrite('problems'))
const tab = ref('list')

const stats = ref<{ byStatus: Record<string, number>; resolved30d: number; avgResolveDays: number } | null>(null)
const openCount = computed(() => (stats.value ? (stats.value.byStatus.NEW || 0) + (stats.value.byStatus.ANALYZING || 0) + (stats.value.byStatus.KNOWN_ERROR || 0) : 0))

const q = reactive({ status: 'OPEN', keyword: '', assetNo: '', page: 1, size: 20 })
const rows = ref<ProblemItem[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const r = await fetchProblems({ status: q.status || undefined, keyword: q.keyword || undefined, assetNo: q.assetNo || undefined, page: q.page, size: q.size })
    rows.value = r.records
    total.value = r.total
    stats.value = await fetchProblemStats()
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}
function search() {
  q.page = 1
  void load()
}

/* ---------------- 建议 ---------------- */
const sug = ref<{ repeatFaults: any[]; repeatAlerts: any[]; modelDefects: any[] } | null>(null)
const sugCount = computed(() => (sug.value ? sug.value.repeatFaults.length + sug.value.repeatAlerts.length + sug.value.modelDefects.length : 0))
async function loadSug() {
  try {
    sug.value = await fetchProblemSuggestions()
  } catch {
    // ignore
  }
}

/* ---------------- 新建 / 编辑 ---------------- */
const dialog = ref(false)
const editingNo = ref<string | null>(null)
const form = reactive<ProblemPayload & { assetNos: string[] }>({
  title: '',
  description: '',
  priority: '中',
  owner: '',
  assetNos: [],
  modelCode: '',
  alertname: '',
  source: 'MANUAL',
  links: [],
})
function openCreate(prefill?: Partial<ProblemPayload>) {
  editingNo.value = null
  Object.assign(form, { title: '', description: '', priority: '中', owner: auth.user?.username || '', assetNos: [], modelCode: '', alertname: '', source: 'MANUAL', links: [] }, prefill || {})
  dialog.value = true
}
function openEdit(p: ProblemDetail) {
  editingNo.value = p.prbNo
  Object.assign(form, {
    title: p.title,
    description: p.description || '',
    priority: p.priority,
    owner: p.owner || '',
    assetNos: [...(p.assetNos || [])],
    modelCode: p.modelCode || '',
    alertname: p.alertname || '',
    links: [],
  })
  dialog.value = true
}
async function save() {
  if (!form.title.trim()) return ElMessage.warning('请填写标题')
  try {
    const body = { ...form, owner: form.owner || undefined, modelCode: form.modelCode || undefined, alertname: form.alertname || undefined }
    const r = editingNo.value ? await updateProblem(editingNo.value, body) : await createProblem(body)
    ElMessage.success(editingNo.value ? '已保存' : `已创建 ${r.prbNo}`)
    dialog.value = false
    void load()
    if (tab.value === 'suggest') void loadSug()
    void openDetail(r.prbNo)
  } catch {
    // ignore
  }
}
function fromFault(s: any) {
  openCreate({
    title: `${s.assetNo} ${s.assetName || ''} 30 天内 ${s.n} 次故障报修`,
    description: `问题建议：同一资产 30 天内反复报修 ${s.n} 次。`,
    priority: s.n >= 5 ? '高' : '中',
    assetNos: [s.assetNo],
    modelCode: s.modelCode || '',
    source: 'SUGGESTION',
    links: (s.woNos || []).slice(0, 20).map((w: string) => ({ type: 'WORKORDER', ref: w })),
  })
}
function fromAlert(s: any) {
  openCreate({
    title: `${s.assetNo} 反复触发 ${s.alertname}（7 天 ${s.episodes} 次）`,
    description: `问题建议：同一资产同一告警 7 天内发作 ${s.episodes} 次。`,
    priority: '中',
    assetNos: [s.assetNo],
    modelCode: s.modelCode || '',
    alertname: s.alertname,
    source: 'SUGGESTION',
    links: (s.alertIds || []).slice(0, 20).map((a: string) => ({ type: 'ALERT', ref: a })),
  })
}
function fromModel(s: any) {
  openCreate({
    title: `型号 ${s.modelCode} 30 天内 ${s.assets} 台故障（疑似批次 / 固件问题）`,
    description: `问题建议：同型号 30 天内 ${s.assets} 台设备共 ${s.faults} 次故障报修。`,
    priority: s.assets >= 5 ? '高' : '中',
    assetNos: s.assetNos || [],
    modelCode: s.modelCode,
    source: 'SUGGESTION',
  })
}

/* ---------------- 详情 ---------------- */
const drawer = ref(false)
const detail = ref<ProblemDetail | null>(null)
async function openDetail(no: string) {
  try {
    detail.value = await fetchProblem(no)
    drawer.value = true
  } catch {
    // ignore
  }
}

/* 流转 */
const tDialog = ref(false)
const tForm = reactive({ to: '', rootCause: '', workaround: '', resolution: '', comment: '', createKb: true })
const NEXT_LABEL: Record<string, string> = {
  ANALYZING: '开始分析',
  KNOWN_ERROR: '登记为已知错误',
  RESOLVED: '标记已解决',
  CLOSED: '关闭',
}
function openTransition(to: string) {
  const d = detail.value!
  Object.assign(tForm, { to, rootCause: d.rootCause || '', workaround: d.workaround || '', resolution: d.resolution || '', comment: '', createKb: true })
  tDialog.value = true
}
async function doTransition() {
  const d = detail.value!
  try {
    detail.value = await transitionProblem(d.prbNo, { ...tForm })
    tDialog.value = false
    ElMessage.success(`已${NEXT_LABEL[tForm.to]}${detail.value.kbNo && tForm.to === 'KNOWN_ERROR' ? `，已生成知识库文章 ${detail.value.kbNo}` : ''}`)
    void load()
  } catch {
    // ignore
  }
}

/* 关联 */
const link = reactive({ type: 'WORKORDER', ref: '' })
async function addLink() {
  if (!link.ref.trim() || !detail.value) return
  try {
    detail.value = await linkProblem(detail.value.prbNo, link.type, link.ref.trim())
    link.ref = ''
  } catch {
    // ignore
  }
}
async function removeLink(id: number) {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('解除该关联？', '确认', { type: 'warning' })
    await unlinkProblem(detail.value.prbNo, id)
    detail.value = await fetchProblem(detail.value.prbNo)
  } catch {
    // 取消
  }
}
function goLink(l: { linkType: string; refNo: string }) {
  if (l.linkType === 'WORKORDER') router.push(`/workorders/${l.refNo}`)
  else if (l.linkType === 'ALERT') router.push({ path: '/monitoring/alerts', query: {} })
  else router.push({ path: '/changes' })
}
const LINK_LABEL: Record<string, string> = { WORKORDER: '工单', ALERT: '告警', CHANGE: '变更' }

function fmt(v?: string) {
  return v ? v.replace('T', ' ').slice(0, 16) : '-'
}

onMounted(() => {
  if (typeof route.query.asset === 'string' && route.query.asset) {
    q.assetNo = route.query.asset
    q.status = ''
  }
  void load()
  void loadSug()
  const prb = route.query.prb
  if (typeof prb === 'string' && prb) void openDetail(prb)
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">问题管理</div>
    <div class="cards">
      <div class="card link" @click="(q.status = 'OPEN'), (tab = 'list'), search()">
        <div class="num c-danger">{{ openCount }}</div>
        <div class="lbl">未解决问题</div>
      </div>
      <div class="card link" @click="(q.status = 'KNOWN_ERROR'), (tab = 'list'), search()">
        <div class="num">{{ stats?.byStatus.KNOWN_ERROR ?? 0 }}</div>
        <div class="lbl">已知错误（有临时方案）</div>
      </div>
      <div class="card">
        <div class="num c-success">{{ stats?.resolved30d ?? 0 }}</div>
        <div class="lbl">近 30 天解决</div>
      </div>
      <div class="card">
        <div class="num">{{ stats?.avgResolveDays ?? 0 }}<small> 天</small></div>
        <div class="lbl">平均解决周期</div>
      </div>
      <div class="card link" @click="tab = 'suggest'">
        <div class="num c-warning">{{ sugCount }}</div>
        <div class="lbl">待评估的问题建议</div>
      </div>
    </div>

    <el-tabs v-model="tab" @tab-change="(n: any) => n === 'suggest' && loadSug()">
      <el-tab-pane label="问题列表" name="list">
        <div class="page-toolbar">
          <el-radio-group v-model="q.status" @change="search">
            <el-radio-button value="OPEN">未解决</el-radio-button>
            <el-radio-button v-for="(l, k) in PROBLEM_STATUS_LABEL" :key="k" :value="k">{{ l }}</el-radio-button>
            <el-radio-button value="">全部</el-radio-button>
          </el-radio-group>
          <el-input v-model="q.keyword" placeholder="编号 / 标题 / 根因 / 告警 / 型号" clearable style="width: 240px" @keyup.enter="search" @clear="search" />
          <el-tag v-if="q.assetNo" closable @close="(q.assetNo = ''), search()">资产 {{ q.assetNo }}</el-tag>
          <el-button v-if="q.assetNo && canWrite" size="small" @click="openCreate({ assetNos: [q.assetNo], title: q.assetNo + ' ' })">为该资产登记问题</el-button>
          <div style="flex: 1"></div>
          <el-button v-if="canWrite" type="primary" :icon="'Plus'" @click="openCreate()">新建问题</el-button>
        </div>
        <el-table v-loading="loading" :data="rows" border stripe size="small">
          <el-table-column label="问题" min-width="300">
            <template #default="{ row }">
              <el-link type="primary" @click="openDetail(row.prbNo)">{{ row.prbNo }}</el-link>
              <el-tag v-if="row.source === 'SUGGESTION'" size="small" type="warning" effect="plain" style="margin-left: 6px">自动发现</el-tag>
              <div>{{ row.title }}</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag size="small" :type="PROBLEM_STATUS_TAG[row.status]">{{ PROBLEM_STATUS_LABEL[row.status] }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="priority" label="优先级" width="70" />
          <el-table-column label="影响" width="150">
            <template #default="{ row }">
              <span class="sub">{{ row.assetNos.length }} 台资产 · {{ row.woCount }} 工单 · {{ row.alertCount }} 告警</span>
            </template>
          </el-table-column>
          <el-table-column prop="owner" label="负责人" width="100" />
          <el-table-column label="知识" width="110">
            <template #default="{ row }">
              <el-link v-if="row.kbNo" type="primary" @click="router.push({ path: '/knowledge', query: { kb: row.kbNo } })">{{ row.kbNo }}</el-link>
              <span v-else class="sub">-</span>
            </template>
          </el-table-column>
          <el-table-column label="更新" width="140">
            <template #default="{ row }">{{ fmt(row.updatedAt) }}</template>
          </el-table-column>
        </el-table>
        <div class="page-pagination">
          <el-pagination v-model:current-page="q.page" :page-size="q.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
        </div>
      </el-tab-pane>

      <el-tab-pane :label="`问题建议（${sugCount}）`" name="suggest">
        <el-alert type="info" :closable="false" style="margin-bottom: 12px">
          系统按规则自动发现可能存在共性根因的故障：同一资产 30 天内 ≥ 3 次报修、同一资产同一告警 7 天内 ≥ 3 次发作、同一型号 30 天内 ≥ 3 台故障。
          已被未关闭问题单覆盖的不再提示。
        </el-alert>
        <template v-if="sug">
          <h4 class="sec">同一资产反复报修（{{ sug.repeatFaults.length }}）</h4>
          <el-table :data="sug.repeatFaults" border size="small" empty-text="暂无">
            <el-table-column label="资产" min-width="220">
              <template #default="{ row }">{{ row.assetNo }}<div class="sub">{{ row.assetName }} {{ row.rackCode ? '· ' + row.rackCode : '' }}</div></template>
            </el-table-column>
            <el-table-column prop="modelCode" label="型号" width="140" />
            <el-table-column prop="n" label="30 天报修" width="90" align="right" />
            <el-table-column label="最近工单" min-width="200">
              <template #default="{ row }">{{ (row.woNos || []).slice(0, 3).join('、') }}</template>
            </el-table-column>
            <el-table-column v-if="canWrite" width="100">
              <template #default="{ row }"><el-button link type="primary" @click="fromFault(row)">创建问题</el-button></template>
            </el-table-column>
          </el-table>
          <h4 class="sec">同一资产同一告警反复触发（{{ sug.repeatAlerts.length }}）</h4>
          <el-table :data="sug.repeatAlerts" border size="small" empty-text="暂无">
            <el-table-column label="资产" min-width="220">
              <template #default="{ row }">{{ row.assetNo }}<div class="sub">{{ row.assetName }}</div></template>
            </el-table-column>
            <el-table-column prop="alertname" label="告警" min-width="160" />
            <el-table-column prop="episodes" label="7 天发作" width="90" align="right" />
            <el-table-column label="最近" width="140"><template #default="{ row }">{{ fmt(row.lastAt) }}</template></el-table-column>
            <el-table-column v-if="canWrite" width="100">
              <template #default="{ row }"><el-button link type="primary" @click="fromAlert(row)">创建问题</el-button></template>
            </el-table-column>
          </el-table>
          <h4 class="sec">同型号多台故障（{{ sug.modelDefects.length }}）</h4>
          <el-table :data="sug.modelDefects" border size="small" empty-text="暂无">
            <el-table-column prop="modelCode" label="型号" min-width="160" />
            <el-table-column prop="assets" label="故障台数" width="90" align="right" />
            <el-table-column prop="faults" label="报修次数" width="90" align="right" />
            <el-table-column label="资产" min-width="260">
              <template #default="{ row }">{{ (row.assetNos || []).slice(0, 5).join('、') }}{{ row.assets > 5 ? ' …' : '' }}</template>
            </el-table-column>
            <el-table-column v-if="canWrite" width="100">
              <template #default="{ row }"><el-button link type="primary" @click="fromModel(row)">创建问题</el-button></template>
            </el-table-column>
          </el-table>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="drawer" size="760px" :title="detail ? `${detail.prbNo}　${detail.title}` : ''">
      <template v-if="detail">
        <div class="actions">
          <el-tag :type="PROBLEM_STATUS_TAG[detail.status]">{{ PROBLEM_STATUS_LABEL[detail.status] }}</el-tag>
          <template v-if="canWrite">
            <el-button v-for="n in PROBLEM_NEXT[detail.status]" :key="n" size="small" :type="n === 'RESOLVED' ? 'success' : n === 'CLOSED' ? '' : 'primary'" plain @click="openTransition(n)">
              {{ NEXT_LABEL[n] }}
            </el-button>
            <el-button v-if="detail.status !== 'CLOSED'" size="small" @click="openEdit(detail)">编辑</el-button>
          </template>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="优先级">{{ detail.priority }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ detail.owner || '-' }}</el-descriptions-item>
          <el-descriptions-item label="型号">{{ detail.modelCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="告警">{{ detail.alertname || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建">{{ detail.createdBy }} · {{ fmt(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="知识库">
            <el-link v-if="detail.kbNo" type="primary" @click="router.push({ path: '/knowledge', query: { kb: detail.kbNo } })">{{ detail.kbNo }}</el-link>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="描述" :span="2"><div class="pre">{{ detail.description || '-' }}</div></el-descriptions-item>
          <el-descriptions-item label="根本原因" :span="2"><div class="pre">{{ detail.rootCause || '-' }}</div></el-descriptions-item>
          <el-descriptions-item label="临时方案" :span="2"><div class="pre">{{ detail.workaround || '-' }}</div></el-descriptions-item>
          <el-descriptions-item label="永久方案" :span="2"><div class="pre">{{ detail.resolution || '-' }}</div></el-descriptions-item>
        </el-descriptions>

        <h4 class="sec">受影响资产（{{ detail.assets.length }}）</h4>
        <el-table :data="detail.assets" size="small" border max-height="220">
          <el-table-column prop="assetNo" label="资产" width="190" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="modelCode" label="型号" width="120" />
          <el-table-column prop="rackCode" label="机柜" width="110" />
          <el-table-column prop="statusBiz" label="状态" width="90" />
        </el-table>

        <h4 class="sec">关联事件 / 变更（{{ detail.links.length }}）</h4>
        <div v-if="canWrite && detail.status !== 'CLOSED'" class="link-add">
          <el-select v-model="link.type" style="width: 90px" size="small">
            <el-option v-for="(l, k) in LINK_LABEL" :key="k" :label="l" :value="k" />
          </el-select>
          <el-input v-model="link.ref" size="small" :placeholder="link.type === 'WORKORDER' ? '工单号 WO-…' : link.type === 'ALERT' ? '告警编号（数字）' : '变更单号 CR-…'" style="width: 220px" @keyup.enter="addLink" />
          <el-button size="small" @click="addLink">关联</el-button>
        </div>
        <el-table :data="detail.links" size="small" border>
          <el-table-column label="类型" width="60"><template #default="{ row }">{{ LINK_LABEL[row.linkType] }}</template></el-table-column>
          <el-table-column label="编号" width="170"><template #default="{ row }"><el-link @click="goLink(row)">{{ row.refNo }}</el-link></template></el-table-column>
          <el-table-column prop="title" label="标题" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column label="时间" width="130"><template #default="{ row }">{{ fmt(row.refTime) }}</template></el-table-column>
          <el-table-column v-if="canWrite && detail.status !== 'CLOSED'" width="64">
            <template #default="{ row }"><el-button link type="danger" @click="removeLink(row.id)">解除</el-button></template>
          </el-table-column>
        </el-table>

        <template v-if="detail.recommend.length">
          <h4 class="sec">相关知识</h4>
          <div v-for="k in detail.recommend" :key="k.id" class="kb">
            <el-tag size="small" effect="plain">{{ KB_CATEGORY_LABEL[k.category] }}</el-tag>
            <el-link type="primary" @click="router.push({ path: '/knowledge', query: { kb: k.kbNo } })">{{ k.title }}</el-link>
          </div>
        </template>

        <h4 class="sec">处理记录</h4>
        <el-timeline>
          <el-timeline-item v-for="(f, i) in detail.flow" :key="i" :timestamp="fmt(f.createdAt)" placement="top">
            <b>{{ f.actor }}</b>
            {{ f.fromStatus ? PROBLEM_STATUS_LABEL[f.fromStatus] + ' → ' : '' }}{{ PROBLEM_STATUS_LABEL[f.toStatus] }}
            <div v-if="f.comment" class="sub">{{ f.comment }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>

    <el-dialog v-model="tDialog" :title="NEXT_LABEL[tForm.to]" width="600px">
      <el-form label-width="90px">
        <template v-if="tForm.to === 'KNOWN_ERROR' || tForm.to === 'RESOLVED'">
          <el-form-item label="根本原因" :required="tForm.to === 'KNOWN_ERROR'">
            <el-input v-model="tForm.rootCause" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="临时方案" :required="tForm.to === 'KNOWN_ERROR'">
            <el-input v-model="tForm.workaround" type="textarea" :rows="3" placeholder="一线遇到该问题时如何快速恢复业务" />
          </el-form-item>
        </template>
        <template v-if="tForm.to === 'RESOLVED'">
          <el-form-item label="永久方案" required>
            <el-input v-model="tForm.resolution" type="textarea" :rows="3" placeholder="如：固件升级至 x.y（通过变更 CR-… 实施）" />
          </el-form-item>
          <el-form-item v-if="!detail?.kbNo" label="知识库">
            <el-checkbox v-model="tForm.createKb">同时沉淀为知识库文章</el-checkbox>
          </el-form-item>
        </template>
        <el-form-item label="说明" :required="tForm.to === 'CLOSED' && detail?.status !== 'RESOLVED'">
          <el-input v-model="tForm.comment" type="textarea" :rows="2" :placeholder="tForm.to === 'CLOSED' ? '直接关闭须说明原因，如重复问题、无法复现' : ''" />
        </el-form-item>
        <div v-if="tForm.to === 'KNOWN_ERROR'" class="sub" style="margin-left: 90px">
          登记后自动生成「已知错误」知识库文章，并按告警 / 型号推荐给一线处理人员。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="tDialog = false">取消</el-button>
        <el-button type="primary" @click="doTransition">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialog" :title="editingNo ? '编辑问题' : '新建问题'" width="640px">
      <el-form label-width="80px">
        <el-form-item label="标题" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-radio-group v-model="form.priority">
                <el-radio-button value="高">高</el-radio-button>
                <el-radio-button value="中">中</el-radio-button>
                <el-radio-button value="低">低</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="负责人"><el-input v-model="form.owner" placeholder="登录名" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="型号"><el-input v-model="form.modelCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="告警名"><el-input v-model="form.alertname" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="受影响资产"><AssetPicker v-model="form.assetNos as any" multiple style="width: 100%" /></el-form-item>
        <el-form-item v-if="!editingNo && form.links?.length" label="将关联">
          <span class="sub">{{ form.links.length }} 条{{ form.links[0].type === 'ALERT' ? '告警' : '工单' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 12px;
  margin-bottom: 8px;
}
.card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px 14px;
}
.card.link {
  cursor: pointer;
}
.card.link:hover {
  border-color: var(--el-color-primary);
}
.num {
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.num small {
  font-size: 12px;
  font-weight: 400;
}
.lbl,
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.c-danger {
  color: var(--el-color-danger);
}
.c-warning {
  color: var(--el-color-warning);
}
.c-success {
  color: var(--el-color-success);
}
.sec {
  margin: 16px 0 8px;
  font-size: 14px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.actions .el-button + .el-button {
  margin-left: 0;
}
.link-add {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}
.pre {
  white-space: pre-wrap;
}
.kb {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 4px 0;
}
</style>
