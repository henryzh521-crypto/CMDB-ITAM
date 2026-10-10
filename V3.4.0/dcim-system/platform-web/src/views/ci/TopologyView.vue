<script setup lang="ts">
/**
 * v3.5 网络拓扑：全量 CI 关系图（机房 / 机柜 / 资产）。
 * - 数据来自 GET /api/ci-relations/graph（隐含层级 + 显式关系，服务端 500 节点截断）
 * - 机房过滤 / 关系类型图例筛选（客户端过滤边，节点随之收缩）
 * - 节点点击跳转：资产 → 资产台账，机柜 → 机柜管理，机房 → 机房管理
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import echarts, { CHART_COLORS } from '@/utils/echarts'
import type { EChartsType } from '@/utils/echarts'
import { fetchRooms } from '@/api/room'
import { CI_TYPE_LABEL, fetchFullGraph } from '@/api/ci'
import type { CiFullGraph } from '@/api/ci'
import { escapeHtml } from '@/utils/escape'

const router = useRouter()

/** 关系类型目录（全量图实际会出现的 7 种；label 与后端 CiGraphService 对齐） */
const REL_CATALOG: { value: string; label: string; implicit: boolean }[] = [
  { value: 'DEPENDS_ON', label: '依赖', implicit: false },
  { value: 'RUNS_ON', label: '运行于', implicit: false },
  { value: 'POWERED_BY', label: '供电来自', implicit: false },
  { value: 'CONNECTS_TO', label: '网络连接', implicit: false },
  { value: 'BACKUP_OF', label: '备份自', implicit: false },
  { value: 'CONTAINS', label: '机房/机柜包含', implicit: true },
  { value: 'PART_OF', label: '部件所属', implicit: true },
]

const rooms = ref<{ roomCode: string; name: string }[]>([])
const roomCode = ref('')
const graph = ref<CiFullGraph | null>(null)
const loading = ref(false)
/** 勾选的关系类型（默认全部显示） */
const relFilter = ref<string[]>(REL_CATALOG.map((r) => r.value))

const chartEl = ref<HTMLDivElement>()
let chart: EChartsType | null = null

const TYPE_COLOR: Record<string, string> = { ROOM: CHART_COLORS[5], RACK: CHART_COLORS[2], ASSET: CHART_COLORS[0] }
const TYPE_SIZE: Record<string, number> = { ROOM: 50, RACK: 34, ASSET: 22 }

async function loadRooms() {
  try {
    const res = await fetchRooms({ page: 1, size: 200 })
    rooms.value = (res.records ?? []).map((r) => ({ roomCode: r.roomCode, name: r.name }))
  } catch {
    rooms.value = []
  }
}

async function load() {
  loading.value = true
  try {
    graph.value = await fetchFullGraph(roomCode.value || undefined)
    await nextTick()
    render()
  } catch {
    graph.value = null
  } finally {
    loading.value = false
  }
}

/** 可见边 = 勾选的关系类型；可见节点 = 至少连着一条可见边 */
const visible = computed(() => {
  const g = graph.value
  if (!g) return { nodes: [], edges: [] }
  const edges = g.edges.filter((e) => relFilter.value.includes(e.rel))
  const used = new Set<string>()
  edges.forEach((e) => {
    used.add(e.from)
    used.add(e.to)
  })
  return { nodes: g.nodes.filter((n) => used.has(`${n.type}:${n.id}`)), edges }
})

/** 图例计数：当前数据里各关系类型出现次数（不受筛选影响，便于判断） */
function relCount(v: string): number {
  return graph.value?.edges.filter((e) => e.rel === v).length ?? 0
}

function render() {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  const { nodes, edges } = visible.value
  if (!nodes.length) {
    chart.clear()
    return
  }
  const nameOf = new Map(nodes.map((n) => [`${n.type}:${n.id}`, n.name] as const))
  const data = nodes.map((n) => ({
    id: `${n.type}:${n.id}`,
    name: n.name,
    itemStyle: { color: TYPE_COLOR[n.type] || CHART_COLORS[4] },
    symbolSize: TYPE_SIZE[n.type] ?? 22,
    label: { show: nodes.length < 80, position: 'right', formatter: n.name },
    rawType: n.type,
    rawId: n.id,
    rawStatus: n.status,
  }))
  const links = edges.map((e) => ({
    source: e.from,
    target: e.to,
    rawLabel: e.label,
    label: { show: false, formatter: e.label, fontSize: 10 },
    emphasis: { label: { show: true } },
    lineStyle: { type: e.implicit ? 'dashed' : 'solid', opacity: 0.65 },
  }))
  chart.setOption(
    {
      tooltip: {
        formatter: (p: any) => {
          if (p.dataType === 'node') {
            const d = p.data
            return `${escapeHtml(CI_TYPE_LABEL[d.rawType] || d.rawType)}　${escapeHtml(d.rawId)}<br/>${escapeHtml(d.name)}${d.rawStatus ? '　' + escapeHtml(d.rawStatus) : ''}<br/><span style="color:#909399">点击节点跳转</span>`
          }
          if (p.dataType === 'edge') {
            const s = nameOf.get(p.data.source)
            const t = nameOf.get(p.data.target)
            return `${escapeHtml(p.data.rawLabel)}<br/>${escapeHtml(s || p.data.source)} → ${escapeHtml(t || p.data.target)}`
          }
          return ''
        },
      },
      series: [
        {
          type: 'graph',
          layout: 'force',
          roam: true,
          draggable: true,
          force: { repulsion: 300, edgeLength: [60, 140], gravity: 0.08 },
          edgeSymbol: ['none', 'arrow'],
          edgeSymbolSize: 7,
          data,
          links,
          emphasis: { focus: 'adjacency' },
        },
      ],
    },
    true,
  )
  chart.off('click')
  chart.on('click', (p: any) => {
    if (p.dataType !== 'node') return
    const { rawType, rawId } = p.data
    if (rawType === 'ASSET') {
      void router.push({ path: '/assets/inventory', query: { keyword: rawId } })
    } else if (rawType === 'RACK') {
      void router.push({ path: '/racks', query: { keyword: rawId } })
    } else if (rawType === 'ROOM') {
      void router.push({ path: '/rooms', query: { keyword: rawId } })
    }
  })
}

function onResize() {
  chart?.resize()
}

watch(relFilter, () => render())

onMounted(() => {
  window.addEventListener('resize', onResize)
  void loadRooms()
  void load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">网络拓扑</div>

    <div class="page-toolbar">
      <el-select
        v-model="roomCode"
        placeholder="全部机房"
        clearable
        filterable
        style="width: 220px"
        @change="load"
      >
        <el-option v-for="r in rooms" :key="r.roomCode" :label="`${r.name}（${r.roomCode}）`" :value="r.roomCode" />
      </el-select>
      <el-button type="primary" plain @click="load">刷新</el-button>
      <div style="flex: 1"></div>
      <span v-if="graph" class="hint">
        资产 {{ graph.summary?.ASSET ?? 0 }} · 机柜 {{ graph.summary?.RACK ?? 0 }} · 机房 {{ graph.summary?.ROOM ?? 0 }}
        <el-tag v-if="graph.truncated" type="warning" size="small" style="margin-left: 6px">已截断（仅显示前 500 个节点）</el-tag>
      </span>
    </div>

    <!-- 关系图例（筛选） -->
    <div class="rel-filter">
      <el-checkbox-group v-model="relFilter">
        <el-checkbox v-for="r in REL_CATALOG" :key="r.value" :value="r.value">
          {{ r.label }}（{{ relCount(r.value) }}）
        </el-checkbox>
      </el-checkbox-group>
    </div>

    <div class="chart-wrap">
      <div ref="chartEl" v-loading="loading" class="chart"></div>
      <el-empty
        v-if="!loading && !visible.nodes.length"
        description="当前筛选下没有可见的关系，勾选上方关系类型或切换机房"
        class="chart-empty"
      />
    </div>

    <div class="legend">
      <span><i :style="{ background: TYPE_COLOR.ROOM }"></i>机房</span>
      <span><i :style="{ background: TYPE_COLOR.RACK }"></i>机柜</span>
      <span><i :style="{ background: TYPE_COLOR.ASSET }"></i>资产</span>
      <span>实线 = 登记的关系　虚线 = 物理位置/部件推导　滚轮缩放 / 拖拽平移　点击节点跳转台账</span>
    </div>
  </div>
</template>

<style scoped>
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.rel-filter {
  margin-bottom: 10px;
}
.rel-filter :deep(.el-checkbox) {
  margin-right: 16px;
}
.chart-wrap {
  position: relative;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.chart {
  height: 560px;
}
.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;
}
.legend i {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: -1px;
}
</style>
