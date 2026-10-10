<script setup lang="ts">
/**
 * 机柜视图：单柜 2D 立面 / 3D 立体一键切换，U 位详情点击高亮。
 */
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchRackUnits, fetchRacks, fetchRooms } from '@/api/room'
import type { RackItem, RackUnit, RoomItem } from '@/api/room'
import RackElevation2D from './components/RackElevation2D.vue'
import type { UnitBlock } from './floor-utils'

const route = useRoute()
const router = useRouter()

const RackElevation3D = defineAsyncComponent(() => import('./components/RackElevation3D.vue'))

const roomOptions = ref<RoomItem[]>([])
const rackOptions = ref<RackItem[]>([])
const roomCode = ref('')
const rackCode = ref('')
const mode = ref<'2d' | '3d'>('2d')

const units = ref<RackUnit[]>([])
const unitsLoading = ref(false)
const currentRack = computed(() => rackOptions.value.find((r) => r.rackCode === rackCode.value) || null)

const uCapacity = computed(() => {
  const fromUnits = units.value.length ? Math.max(...units.value.map((u) => u.unitNo)) : 0
  return currentRack.value?.uCapacity ?? fromUnits ?? 42
})

const usedUnits = computed(() => units.value.filter((u) => u.status === '占用').length)
const freeUnits = computed(() => Math.max(uCapacity.value - usedUnits.value, 0))
const usageRate = computed(() =>
  uCapacity.value ? ((usedUnits.value / uCapacity.value) * 100).toFixed(1) : '0.0',
)

async function loadRooms() {
  try {
    const res = await fetchRooms({ page: 1, size: 200 })
    roomOptions.value = res.records ?? []
  } catch {
    // 拦截器已提示
  }
}

async function loadRackOptions() {
  if (!roomCode.value) {
    rackOptions.value = []
    return
  }
  try {
    const res = await fetchRacks({ page: 1, size: 500, roomCode: roomCode.value })
    rackOptions.value = res.records ?? []
  } catch {
    // 拦截器已提示
  }
}

async function loadUnits() {
  if (!rackCode.value) return
  unitsLoading.value = true
  try {
    const res = await fetchRackUnits(rackCode.value)
    units.value = res.units ?? []
  } catch {
    units.value = []
  } finally {
    unitsLoading.value = false
  }
}

function onRoomChange() {
  rackCode.value = ''
  units.value = []
  void loadRackOptions()
}

function onRackChange() {
  router.replace({ query: { code: rackCode.value } })
  void loadUnits()
}

/** 从机房平面跳入：code 已知时反查机房并加载 */
async function bootstrapFromQuery() {
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  await loadRooms()
  if (code) {
    // 先按关键字定位机柜（拿到 roomCode 后再装入选项）
    try {
      const res = await fetchRacks({ page: 1, size: 20, keyword: code })
      const hit = (res.records ?? []).find((r) => r.rackCode === code)
      if (hit) {
        roomCode.value = hit.roomCode
        rackCode.value = hit.rackCode
        await loadRackOptions()
        // 确保选项里有（keyword 查询可能未进当前机房列表）
        if (!rackOptions.value.some((r) => r.rackCode === code)) {
          rackOptions.value = [hit, ...rackOptions.value]
        }
        await loadUnits()
        return
      }
    } catch {
      // 拦截器已提示
    }
  }
  if (roomOptions.value.length && !roomCode.value) {
    roomCode.value = roomOptions.value[0].roomCode
  }
  await loadRackOptions()
  if (rackOptions.value.length && !rackCode.value) {
    rackCode.value = rackOptions.value[0].rackCode
    await loadUnits()
  }
}

/** 3D 高亮点击的设备段 */
const highlight = ref<UnitBlock | null>(null)

function onBlockSelect(b: UnitBlock) {
  highlight.value = b
}

watch(mode, (m) => {
  if (m === '3d') void import('./components/RackElevation3D.vue')
})

onMounted(() => {
  void bootstrapFromQuery()
})
</script>

<template>
  <div class="page-card view-page">
    <div class="page-title">机柜视图</div>

    <div class="page-toolbar">
      <el-select
        v-model="roomCode"
        placeholder="选择机房"
        filterable
        style="width: 220px"
        @change="onRoomChange"
      >
        <el-option
          v-for="r in roomOptions"
          :key="r.roomCode"
          :label="`${r.roomCode}（${r.name}）`"
          :value="r.roomCode"
        />
      </el-select>
      <el-select
        v-model="rackCode"
        placeholder="选择机柜"
        filterable
        style="width: 220px"
        :loading="unitsLoading"
        @change="onRackChange"
      >
        <el-option v-for="r in rackOptions" :key="r.rackCode" :label="r.rackCode" :value="r.rackCode" />
      </el-select>

      <el-radio-group v-model="mode">
        <el-radio-button value="2d">2D 立面</el-radio-button>
        <el-radio-button value="3d">3D 立体</el-radio-button>
      </el-radio-group>

      <div style="flex: 1"></div>
      <el-button @click="loadUnits" :loading="unitsLoading">刷新</el-button>
    </div>

    <!-- 机柜信息条 -->
    <div v-if="currentRack" class="rack-meta">
      <div class="rm">
        <span class="rm-label">U 容量</span>
        <span class="rm-value">{{ uCapacity }}U</span>
      </div>
      <div class="rm">
        <span class="rm-label">已用 / 空闲</span>
        <span class="rm-value">{{ usedUnits }} / {{ freeUnits }}U</span>
      </div>
      <div class="rm">
        <span class="rm-label">使用率</span>
        <span class="rm-value">{{ usageRate }}%</span>
      </div>
      <div class="rm">
        <span class="rm-label">位置</span>
        <span class="rm-value">
          {{ currentRack.locationName || '-' }}
          <template v-if="currentRack.rowNo != null"> · {{ currentRack.rowNo }}排</template>
          <template v-if="currentRack.colNo != null"> · {{ currentRack.colNo }}列</template>
        </span>
      </div>
      <div class="rm">
        <span class="rm-label">供电上限</span>
        <span class="rm-value">{{ currentRack.powerLimitKw ?? '-' }} kW</span>
      </div>
      <div class="rm">
        <span class="rm-label">制冷 / PDU</span>
        <span class="rm-value">{{ currentRack.coolingType || '-' }} / {{ currentRack.pduCount ?? '-' }}</span>
      </div>
      <div class="rm">
        <span class="rm-label">负责人</span>
        <span class="rm-value">{{ currentRack.manager || '-' }}</span>
      </div>
      <div v-if="highlight" class="rm hl">
        <span class="rm-label">选中</span>
        <span class="rm-value">
          U{{ highlight.fromU }}-U{{ highlight.toU }}
          {{ highlight.status === '占用' ? (highlight.assetName || highlight.occupiedBy || '') : highlight.status }}
        </span>
      </div>
    </div>

    <div v-loading="unitsLoading" class="view-stage">
      <div v-show="mode === '2d'" class="stage-2d">
        <RackElevation2D
          v-if="rackCode"
          :units="units"
          :u-capacity="uCapacity"
          :rack-code="rackCode"
          @select="onBlockSelect"
        />
        <el-empty v-else description="请选择机柜" :image-size="60" />
      </div>
      <RackElevation3D
        v-if="mode === '3d' && rackCode"
        :units="units"
        :u-capacity="uCapacity"
        :rack-code="rackCode"
        @select="onBlockSelect"
      />
    </div>
  </div>
</template>

<style scoped>
.view-page {
  display: flex;
  flex-direction: column;
}

.rack-meta {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  padding: 10px 14px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 6px;
  margin-bottom: 12px;
}

.rm {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rm.hl .rm-value {
  color: var(--el-color-primary);
}

.rm-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rm-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.view-stage {
  position: relative;
  min-height: 520px;
}

.stage-2d {
  display: flex;
  justify-content: center;
  padding: 4px 0 12px;
}

.view-stage > :deep(.elev3d) {
  height: calc(100vh - 320px);
  min-height: 480px;
}
</style>
