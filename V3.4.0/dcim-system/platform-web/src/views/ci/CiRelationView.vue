<script setup lang="ts">
/**
 * CMDB 配置项关系与影响分析
 * - 选择配置项（资产 / 机柜 / 机房）→ 查看其全部显式关系（出向 + 入向）
 * - 影响分析（谁会被波及）/ 依赖分析（我依赖谁）：拓扑图 + 清单，含机房→机柜→设备隐式拓扑
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import echarts, { CHART_COLORS } from '@/utils/echarts'
import type { EChartsType } from '@/utils/echarts'
import AssetPicker from '@/components/AssetPicker.vue'
import { useAuthStore } from '@/stores/auth'
import {
  CI_TYPE_LABEL,
  createCiRelation,
  deleteCiRelation,
  fetchCiMeta,
  fetchCiRelations,
  fetchImpactGraph,
} from '@/api/ci'
import type { CiGraph, CiMeta, CiRelation } from '@/api/ci'
import { escapeHtml } from '@/utils/escape'

const auth = useAuthStore()
const route = useRoute()
const canWrite = computed(() => auth.canWrite('assets'))

const meta = ref<CiMeta>({ relTypes: [], ciTypes: [] })
const target = reactive({ type: 'ASSET', id: '' })
const relations = ref<CiRelation[]>([])
const relLoading = ref(false)

const direction = ref<'downstream' | 'upstream'>('downstream')
const graph = ref<CiGraph | null>(null)
const graphLoading = ref(false)
const chartEl = ref<HTMLDivElement>()
let chart: EChartsType | null = null

const TYPE_COLOR: Record<string, string> = { ROOM: CHART_COLORS[5], RACK: CHART_COLORS[2], ASSET: CHART_COLORS[0] }

async function loadAll() {
  if (!target.id) {
    relations.value = []
    graph.value = null
    return
  }
  await Promise.all([loadRelations(), loadGraph()])
}

async function loadRelations() {
  relLoading.value = true
  try {
    relations.value = await fetchCiRelations(target.type, target.id!)
  } catch {
    relations.value = []
  } finally {
    relLoading.value = false
  }
}

async function loadGraph() {
  graphLoading.value = true
  try {
    graph.value = await fetchImpactGraph(target.type, target.id!, direction.value)
    await nextTick()
    renderGraph()
  } catch {
    graph.value = null
  } finally {
    graphLoading.value = false
  }
}

function renderGraph() {
  if (!chartEl.value || !graph.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  const g = graph.value
  const nodes = g.nodes.map((n) => ({
    id: `${n.type}:${n.id}`,
    name: n.name,
    value: n.id,
    symbolSize: n.depth === 0 ? 46 : Math.max(18, 34 - n.depth * 4),
    itemStyle: { color: n.depth === 0 ? CHART_COLORS[3] : TYPE_COLOR[n.type] || CHART_COLORS[4] },
    label: { show: n.depth === 0 || g.nodes.length < 60, position: 'right', formatter: n.name },
    tooltip: { formatter: `${escapeHtml(CI_TYPE_LABEL[n.type] || n.type)}　${escapeHtml(n.id)}<br/>${escapeHtml(n.name)}${n.status ? '　' + escapeHtml(n.status) : ''}<br/>层级 ${n.depth}` },
  }))
  const links = g.edges.map((e) => ({
    source: e.from,
    target: e.to,
    label: { show: false, formatter: e.label, fontSize: 10 },
    emphasis: { label: { show: true } },
    lineStyle: { type: e.implicit ? 'dashed' : 'solid', opacity: 0.7 },
  }))
  chart.setOption(
    {
      tooltip: {},
      series: [
        {
          type: 'graph',
          layout: 'force',
          roam: true,
          draggable: true,
          force: { repulsion: 320, edgeLength: [80, 160] },
          edgeSymbol: ['none', 'arrow'],
          edgeSymbolSize: 8,
          data: nodes,
          links,
          emphasis: { focus: 'adjacency' },
        },
      ],
    },
    true,
  )
}

function onResize() {
  chart?.resize()
}

/* ---------------- 新建关系 ---------------- */
const dialog = ref(false)
const saving = ref(false)
const form = reactive({ srcType: 'ASSET', srcId: '', relType: 'DEPENDS_ON', dstType: 'ASSET', dstId: '', remark: '' })

function asStr(v: string | string[] | undefined): string {
  return Array.isArray(v) ? v[0] || '' : v || ''
}

function onPickTarget(v: string | string[] | undefined) {
  target.id = asStr(v)
  void loadAll()
}

function openCreate() {
  Object.assign(form, { srcType: target.type, srcId: target.id, relType: 'DEPENDS_ON', dstType: 'ASSET', dstId: '', remark: '' })
  dialog.value = true
}

async function save() {
  if (!form.srcId || !form.dstId) {
    ElMessage.warning('请选择两端配置项')
    return
  }
  saving.value = true
  try {
    await createCiRelation({ ...form, remark: form.remark || undefined })
    ElMessage.success('关系已建立')
    dialog.value = false
    await loadAll()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function remove(row: CiRelation) {
  try {
    await ElMessageBox.confirm(`解除关系「${row.srcName} ${row.relLabel} ${row.dstName}」？（保留历史记录）`, '解除关系', { type: 'warning' })
    await deleteCiRelation(row.id)
    ElMessage.success('已解除')
    await loadAll()
  } catch {
    // 取消
  }
}

watch(direction, () => target.id && void loadGraph())

onMounted(async () => {
  window.addEventListener('resize', onResize)
  try {
    meta.value = await fetchCiMeta()
  } catch {
    // ignore
  }
  const q = route.query
  if (typeof q.id === 'string' && q.id) {
    target.type = typeof q.type === 'string' ? q.type : 'ASSET'
    target.id = q.id
    await loadAll()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">CI 关系与影响分析</div>

    <div class="page-toolbar">
      <el-select v-model="target.type" style="width: 100px" @change="target.id = ''">
        <el-option v-for="t in meta.ciTypes" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <div style="width: 340px">
        <AssetPicker v-if="target.type === 'ASSET'" :model-value="target.id" @update:model-value="onPickTarget" />
        <el-input
          v-else
          v-model="target.id"
          :placeholder="target.type === 'RACK' ? '输入机柜编号' : '输入机房编号'"
          clearable
          @keyup.enter="loadAll"
        />
      </div>
      <el-button type="primary" plain @click="loadAll">分析</el-button>
      <div style="flex: 1"></div>
      <el-button v-if="canWrite && target.id" type="primary" @click="openCreate">新建关系</el-button>
    </div>

    <el-empty v-if="!target.id" description="选择一个配置项，查看它的关系拓扑与影响范围" />

    <template v-else>
      <el-row :gutter="16">
        <el-col :span="14">
          <div class="panel">
            <div class="panel-head">
              <el-radio-group v-model="direction" size="small">
                <el-radio-button value="downstream">影响分析（谁会被波及）</el-radio-button>
                <el-radio-button value="upstream">依赖分析（我依赖谁）</el-radio-button>
              </el-radio-group>
              <span v-if="graph" class="hint">
                共 {{ graph.nodes.length - 1 }} 项
                <template v-for="(n, t) in graph.summary" :key="t">　{{ CI_TYPE_LABEL[t] || t }} {{ n }}</template>
                <el-tag v-if="graph.truncated" type="warning" size="small" style="margin-left: 6px">已截断</el-tag>
              </span>
            </div>
            <div ref="chartEl" v-loading="graphLoading" class="chart"></div>
            <div class="legend">
              <span><i :style="{ background: CHART_COLORS[3] }"></i>当前配置项</span>
              <span><i :style="{ background: TYPE_COLOR.ROOM }"></i>机房</span>
              <span><i :style="{ background: TYPE_COLOR.RACK }"></i>机柜</span>
              <span><i :style="{ background: TYPE_COLOR.ASSET }"></i>资产</span>
              <span>实线 = 登记的关系　虚线 = 物理位置/部件推导</span>
            </div>
          </div>
        </el-col>
        <el-col :span="10">
          <div class="panel">
            <div class="panel-head"><b>受影响清单</b></div>
            <el-table :data="graph?.nodes.filter((n) => n.depth > 0) || []" size="small" max-height="430" border>
              <el-table-column label="类型" width="64">
                <template #default="{ row }">{{ CI_TYPE_LABEL[row.type] || row.type }}</template>
              </el-table-column>
              <el-table-column prop="id" label="编号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
              <el-table-column prop="depth" label="层级" width="56" />
            </el-table>
          </div>
        </el-col>
      </el-row>

      <h4 class="sec">登记的关系</h4>
      <el-table v-loading="relLoading" :data="relations" border stripe size="small">
        <el-table-column label="方向" width="70">
          <template #default="{ row }">
            <el-tag size="small" :type="row.direction === 'OUT' ? 'primary' : 'info'">{{ row.direction === 'OUT' ? '出向' : '入向' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="源" min-width="200">
          <template #default="{ row }">{{ CI_TYPE_LABEL[row.srcType] }} {{ row.srcId }} <span class="sub">{{ row.srcName }}</span></template>
        </el-table-column>
        <el-table-column prop="relLabel" label="关系" width="100" />
        <el-table-column label="目标" min-width="200">
          <template #default="{ row }">{{ CI_TYPE_LABEL[row.dstType] }} {{ row.dstId }} <span class="sub">{{ row.dstName }}</span></template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="登记人" width="100" />
        <el-table-column v-if="canWrite" label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="remove(row)">解除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <el-dialog v-model="dialog" title="新建关系" width="560px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="源">
          <el-select v-model="form.srcType" style="width: 100px; margin-right: 8px">
            <el-option v-for="t in meta.ciTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <div style="flex: 1">
            <AssetPicker v-if="form.srcType === 'ASSET'" :model-value="form.srcId" @update:model-value="(v) => (form.srcId = asStr(v))" />
            <el-input v-else v-model="form.srcId" placeholder="编号" />
          </div>
        </el-form-item>
        <el-form-item label="关系">
          <el-select v-model="form.relType" style="width: 100%">
            <el-option v-for="r in meta.relTypes" :key="r.value" :label="`${r.label}（${r.value}）`" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标">
          <el-select v-model="form.dstType" style="width: 100px; margin-right: 8px">
            <el-option v-for="t in meta.ciTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <div style="flex: 1">
            <AssetPicker v-if="form.dstType === 'ASSET'" :model-value="form.dstId" @update:model-value="(v) => (form.dstId = asStr(v))" />
            <el-input v-else v-model="form.dstId" placeholder="编号" />
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="200" />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon title="语义：源「依赖 / 运行于 / 供电来自」目标；目标故障将影响源。网络连接为双向。" />
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  gap: 8px;
  flex-wrap: wrap;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.chart {
  height: 420px;
}
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.legend i {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: -1px;
}
.sec {
  margin: 18px 0 8px;
  font-size: 14px;
}
.sub {
  color: var(--el-text-color-secondary);
  margin-left: 4px;
}
</style>
