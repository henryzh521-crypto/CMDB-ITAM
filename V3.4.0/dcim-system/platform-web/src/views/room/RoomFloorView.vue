<script setup lang="ts">
/**
 * 机房平面：2D SVG 平面图 / 3D three.js 鸟瞰 一键切换，
 * 容量热力叠加 + 机柜抽屉（内嵌 2D 立面，可跳机柜视图）。
 */
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchRackUnits, fetchRoomFloor, fetchRooms } from '@/api/room'
import type { FloorRack, RackUnit, RoomItem } from '@/api/room'
import RoomFloor2D from './components/RoomFloor2D.vue'
import RackElevation2D from './components/RackElevation2D.vue'
import { buildFloorLayout, heatColor, type HeatMode } from './floor-utils'

const route = useRoute()
const router = useRouter()

/* ---------------- 数据 ---------------- */
const roomOptions = ref<RoomItem[]>([])
const roomCode = ref('')
const floorData = ref<Awaited<ReturnType<typeof fetchRoomFloor>> | null>(null)
const loading = ref(false)

const mode = ref<'2d' | '3d'>('2d')
const heatMode = ref<HeatMode>('usage')
const search = ref('')
const selectedCode = ref('')

async function loadRooms() {
  try {
    const res = await fetchRooms({ page: 1, size: 200 })
    roomOptions.value = res.records ?? []
    if (!roomCode.value && roomOptions.value.length) {
      const q = typeof route.query.code === 'string' ? route.query.code : ''
      roomCode.value = q || roomOptions.value[0].roomCode
    }
  } catch {
    // 拦截器已提示
  }
}

async function loadFloor() {
  if (!roomCode.value) return
  loading.value = true
  try {
    floorData.value = await fetchRoomFloor(roomCode.value)
    selectedCode.value = ''
  } catch {
    floorData.value = null
  } finally {
    loading.value = false
  }
}

function onRoomChange() {
  selectedCode.value = ''
  void loadFloor()
  router.replace({ query: { ...route.query, code: roomCode.value } })
}

const layout = computed(() => buildFloorLayout(floorData.value?.racks ?? []))
const room = computed(() => floorData.value?.room ?? null)
const stats = computed(() => floorData.value?.stats ?? null)

/* ---------------- 机柜抽屉（2D 立面） ---------------- */
const drawerVisible = ref(false)
const drawerRack = ref<FloorRack | null>(null)
const drawerUnits = ref<RackUnit[]>([])
const drawerLoading = ref(false)

async function openRack(rack: FloorRack) {
  selectedCode.value = rack.rackCode
  drawerRack.value = rack
  drawerUnits.value = []
  drawerVisible.value = true
  drawerLoading.value = true
  try {
    const res = await fetchRackUnits(rack.rackCode)
    drawerUnits.value = res.units ?? []
    if (res.uCapacity && drawerRack.value) {
      drawerRack.value = { ...drawerRack.value, uCapacity: res.uCapacity }
    }
  } catch {
    // 拦截器已提示
  } finally {
    drawerLoading.value = false
  }
}

function gotoRackView() {
  if (!drawerRack.value) return
  drawerVisible.value = false
  router.push({ path: '/racks/view', query: { code: drawerRack.value.rackCode } })
}

/* 3D 组件按需挂载：切到 3D 才加载 three.js chunk */
const RoomFloor3D = defineAsyncComponent(() => import('./components/RoomFloor3D.vue'))

watch(mode, (m) => {
  if (m === '3d') void import('./components/RoomFloor3D.vue')
})

onMounted(async () => {
  const q = typeof route.query.code === 'string' ? route.query.code : ''
  await loadRooms()
  if (q) {
    roomCode.value = q
  }
  await loadFloor()
})
</script>

<template>
  <div class="page-card floor-page">
    <div class="page-title">机房平面</div>

    <div class="page-toolbar">
      <el-select
        v-model="roomCode"
        placeholder="选择机房"
        filterable
        style="width: 240px"
        :loading="loading"
        @change="onRoomChange"
      >
        <el-option
          v-for="r in roomOptions"
          :key="r.roomCode"
          :label="`${r.roomCode}（${r.name}）`"
          :value="r.roomCode"
        />
      </el-select>

      <el-radio-group v-model="mode">
        <el-radio-button value="2d">2D 平面</el-radio-button>
        <el-radio-button value="3d">3D 立体</el-radio-button>
      </el-radio-group>

      <el-select v-model="heatMode" style="width: 150px">
        <el-option label="热力：U使用率" value="usage" />
        <el-option label="热力：空闲U" value="free" />
      </el-select>

      <el-input
        v-model="search"
        placeholder="搜索柜号 / 位置"
        clearable
        style="width: 200px"
      />

      <div style="flex: 1"></div>
      <el-button @click="loadFloor" :loading="loading">刷新</el-button>
    </div>

    <!-- 统计条 -->
    <div v-if="stats" class="floor-stats">
      <div class="fs">
        <span class="fs-label">机柜数</span>
        <span class="fs-value">{{ stats.rackCount }}</span>
      </div>
      <div class="fs">
        <span class="fs-label">总U / 已用U</span>
        <span class="fs-value">{{ stats.totalUnits }} / {{ stats.usedUnits }}</span>
      </div>
      <div class="fs">
        <span class="fs-label">空闲U</span>
        <span class="fs-value">{{ stats.freeUnits }}</span>
      </div>
      <div class="fs">
        <span class="fs-label">U使用率</span>
        <span class="fs-value" :style="{ color: heatColor(stats.usageRate) }">
          {{ (stats.usageRate * 100).toFixed(1) }}%
        </span>
      </div>
      <div class="fs">
        <span class="fs-label">面积</span>
        <span class="fs-value">{{ room?.areaSqm ?? '-' }} ㎡</span>
      </div>
      <div class="fs">
        <span class="fs-label">电力/制冷</span>
        <span class="fs-value">{{ room?.powerKw ?? '-' }} / {{ room?.coolingKw ?? '-' }} kW</span>
      </div>
    </div>

    <!-- 平面视图 -->
    <div v-loading="loading" class="floor-stage">
      <RoomFloor2D
        v-show="mode === '2d'"
        :layout="layout"
        :room="room"
        :heat-mode="heatMode"
        :search="search"
        :selected-rack-code="selectedCode"
        @select="openRack"
      />
      <RoomFloor3D
        v-if="mode === '3d'"
        :layout="layout"
        :room="room"
        :heat-mode="heatMode"
        :search="search"
        :selected-rack-code="selectedCode"
        @select="openRack"
      />
    </div>

    <!-- 热力图例 -->
    <div class="floor-legend">
      <span class="lg-title">容量热力</span>
      <span class="lg-bar" :style="{ background: `linear-gradient(90deg, ${heatColor(0)}, ${heatColor(0.5)}, ${heatColor(1)})` }"></span>
      <span class="lg-tip">{{ heatMode === 'free' ? '空闲少 → 空闲多' : '空 → 满' }}</span>
      <span class="lg-sep"></span>
      <span class="lg-item"><i class="tri"></i> 机柜正面朝向</span>
      <span class="lg-item">点击机柜查看 U 位立面</span>
    </div>

    <!-- 机柜抽屉 -->
    <el-drawer v-model="drawerVisible" size="420px" :with-header="false">
      <div v-if="drawerRack" class="drawer-box" v-loading="drawerLoading">
        <div class="drawer-head">
          <div>
            <div class="drawer-code">{{ drawerRack.rackCode }}</div>
            <div class="drawer-sub">
              {{ drawerRack.locationName || '未填位置' }}
              <template v-if="drawerRack.rowNo != null"> · {{ drawerRack.rowNo }}排</template>
              <template v-if="drawerRack.colNo != null"> · {{ drawerRack.colNo }}列</template>
            </div>
          </div>
          <el-button type="primary" size="small" @click="gotoRackView">打开机柜视图</el-button>
        </div>
        <div class="drawer-meta">
          <el-tag size="small">已用 {{ drawerRack.usedUnits }}/{{ drawerRack.uCapacity }}U</el-tag>
          <el-tag size="small" type="info">使用率 {{ ((drawerRack.usageRate || 0) * 100).toFixed(1) }}%</el-tag>
          <el-tag v-if="drawerRack.powerLimitKw != null" size="small" type="warning">
            ≤{{ drawerRack.powerLimitKw }}kW
          </el-tag>
          <el-tag v-if="drawerRack.coolingType" size="small" type="success">
            {{ drawerRack.coolingType }}
          </el-tag>
        </div>
        <RackElevation2D
          :units="drawerUnits"
          :u-capacity="drawerRack.uCapacity"
          :rack-code="drawerRack.rackCode"
          compact
        />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.floor-page {
  display: flex;
  flex-direction: column;
}

.floor-stats {
  display: flex;
  gap: 26px;
  flex-wrap: wrap;
  padding: 10px 14px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 6px;
  margin-bottom: 12px;
}

.fs {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.fs-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.fs-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  font-family: Consolas, Monaco, monospace;
}

.floor-stage {
  height: calc(100vh - 330px);
  min-height: 440px;
  position: relative;
}

.floor-stage > * {
  position: absolute;
  inset: 0;
}

.floor-legend {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  flex-wrap: wrap;
}

.lg-title {
  color: var(--el-text-color-secondary);
}

.lg-bar {
  width: 120px;
  height: 10px;
  border-radius: 5px;
  display: inline-block;
}

.lg-tip {
  color: var(--el-text-color-secondary);
}

.lg-sep {
  width: 1px;
  height: 14px;
  background: #e4e7ed;
  margin: 0 4px;
}

.lg-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.tri {
  width: 0;
  height: 0;
  border-left: 5px solid transparent;
  border-right: 5px solid transparent;
  border-top: 7px solid #6b7280;
}

.drawer-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.drawer-code {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  font-family: Consolas, Monaco, monospace;
}

.drawer-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.drawer-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
