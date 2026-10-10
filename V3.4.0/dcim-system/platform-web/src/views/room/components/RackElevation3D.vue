<script setup lang="ts">
/**
 * 机柜 3D 立体（three.js，按需懒加载）：
 * 写实化机柜（底座脚轮 / 四立柱 / 19" 前后导轨与 U 位安装孔 / 可选网孔前门），
 * 设备带面板、提手与状态指示灯，PBR 材质 + 主光阴影 + 浅色机房背景，
 * raycast 拾取设备，悬停高亮、点击选中，独立旋转（OrbitControls）。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { RackUnit } from '@/api/room'
import { mergeUnitBlocks, type UnitBlock } from '../floor-utils'

const props = defineProps<{
  units: RackUnit[]
  uCapacity: number
  rackCode?: string
}>()

const emit = defineEmits<{ select: [block: UnitBlock] }>()

const hostRef = ref<HTMLDivElement>()
const tooltip = ref<{ show: boolean; x: number; y: number; text: string }>({
  show: false,
  x: 0,
  y: 0,
  text: '',
})
const loadError = ref('')
/** 是否显示前门（网孔门，默认隐藏以便看清内部设备） */
const showDoor = ref(false)

/* 柜体（米级近似）：0.6 宽 × 1.0 深 × 高度按 U */
const RACK_W = 0.6
const RACK_D = 1.0
/** 19 英寸导轨间距 */
const RAIL_SPAN = 0.46

/* 浅色机房背景（替代原深色，解决"太黑看不清机柜"） */
const BG = 0xeef2f6

function uSize(): number {
  return 2.0 / Math.max(props.uCapacity || 42, 1)
}

const STATUS_COLORS: Record<string, number> = {
  占用: 0x3b82f6,
  预留: 0xf59e0b,
  故障: 0xef4444,
}
/** 状态指示灯（emissive） */
const LED_COLORS: Record<string, number> = {
  占用: 0x22c55e,
  预留: 0xfbbf24,
  故障: 0xf87171,
}

interface SceneCtx {
  THREE: typeof import('three')
  renderer: import('three').WebGLRenderer
  scene: import('three').Scene
  camera: import('three').PerspectiveCamera
  controls: { update: () => void; dispose: () => void; target: import('three').Vector3 }
  raycaster: import('three').Raycaster
  pointer: import('three').Vector2
  group: import('three').Group
  floor: import('three').Mesh
  envTex?: import('three').Texture
  meshes: import('three').Mesh[]
  raf: number
  ro?: ResizeObserver
  disposed: boolean
}

let ctx: SceneCtx | null = null
let rackH = 2.0
let floorY = -1.1
/** 悬停 / 选中设备（用于 emissive 高亮，rebuild 后需重算） */
let hoverMesh: import('three').Mesh | null = null
let selectedKey = ''

function blockKey(b: UnitBlock): string {
  return `${b.fromU}-${b.toU}-${b.status}`
}

function clearGroup(c: SceneCtx) {
  c.group.traverse((obj) => {
    const mesh = obj as import('three').Mesh
    if (mesh.geometry) mesh.geometry.dispose()
    const m = mesh.material as import('three').Material | import('three').Material[] | undefined
    if (Array.isArray(m)) m.forEach((x) => x.dispose())
    else if (m) m.dispose()
  })
  c.group.clear()
  c.meshes = []
  hoverMesh = null
}

/** 悬停 / 选中高亮：遍历设备（≤U 数）重设 emissive */
function applyGlow() {
  const c = ctx
  if (!c) return
  for (const m of c.meshes) {
    const mat = m.material as import('three').MeshStandardMaterial
    if (!mat || typeof mat.emissive === 'undefined') continue
    const b = m.userData.block as UnitBlock | undefined
    const selected = !!b && blockKey(b) === selectedKey
    const hovered = hoverMesh === m
    if (selected) {
      mat.emissive.setHex(0x1d4ed8)
      mat.emissiveIntensity = 0.45
    } else if (hovered) {
      mat.emissive.setHex(0xffffff)
      mat.emissiveIntensity = 0.18
    } else {
      mat.emissive.setHex(0x000000)
      mat.emissiveIntensity = 1
    }
  }
}

function rebuild() {
  const c = ctx
  if (!c) return
  const { THREE, group } = c
  clearGroup(c)

  const blocks = mergeUnitBlocks(props.units, props.uCapacity)
  const us = uSize()
  const capU = Math.max(props.uCapacity, 1)
  rackH = capU * us + 0.12 // 留顶底边框
  const half = rackH / 2
  floorY = -half - 0.11
  c.floor.position.y = floorY

  /* ── 材质（PBR：金属框架 / 塑料面板 / 橡胶脚轮） ── */
  const steel = new THREE.MeshStandardMaterial({ color: 0x7f8a97, metalness: 0.7, roughness: 0.42 })
  const steelDark = new THREE.MeshStandardMaterial({ color: 0x50596a, metalness: 0.6, roughness: 0.5 })
  const panel = new THREE.MeshStandardMaterial({ color: 0xa6b1bf, metalness: 0.5, roughness: 0.55 })
  const backPanel = new THREE.MeshStandardMaterial({ color: 0x79838f, metalness: 0.45, roughness: 0.6 })
  const railMat = new THREE.MeshStandardMaterial({ color: 0xd6dce4, metalness: 0.85, roughness: 0.28 })
  const holeMat = new THREE.MeshStandardMaterial({ color: 0x2c3540, metalness: 0.3, roughness: 0.75 })
  const rubber = new THREE.MeshStandardMaterial({ color: 0x2f3742, metalness: 0.05, roughness: 0.95 })

  const put = (geo: import('three').BufferGeometry, mat: import('three').Material, x = 0, y = 0, z = 0) => {
    const m = new THREE.Mesh(geo, mat)
    m.position.set(x, y, z)
    m.castShadow = true
    m.receiveShadow = true
    group.add(m)
    return m
  }

  /* ── 底座 + 4 个脚轮 ── */
  put(new THREE.BoxGeometry(RACK_W + 0.07, 0.06, RACK_D + 0.07), steelDark, 0, -half - 0.03, 0)
  const casterGeo = new THREE.CylinderGeometry(0.035, 0.035, 0.05, 16)
  for (const sx of [-1, 1]) {
    for (const sz of [-1, 1]) {
      put(casterGeo, rubber, (sx * (RACK_W - 0.08)) / 2, -half - 0.085, (sz * (RACK_D - 0.12)) / 2)
    }
  }

  /* ── 四根立柱 + 左右侧板 + 背板 + 顶盖 ── */
  const postGeo = new THREE.BoxGeometry(0.05, rackH, 0.05)
  for (const sx of [-1, 1]) {
    for (const sz of [-1, 1]) {
      put(postGeo, steel, (sx * (RACK_W - 0.05)) / 2, 0, (sz * (RACK_D - 0.05)) / 2)
    }
  }
  const sideGeo = new THREE.BoxGeometry(0.025, rackH - 0.02, RACK_D - 0.1)
  for (const sx of [-1, 1]) put(sideGeo, panel, (sx * RACK_W) / 2, 0, 0)
  put(new THREE.BoxGeometry(RACK_W - 0.1, rackH - 0.08, 0.02), backPanel, 0, 0, -RACK_D / 2 + 0.05)
  // 顶盖 + 顶部走线/风机条
  put(new THREE.BoxGeometry(RACK_W + 0.07, 0.05, RACK_D + 0.07), steelDark, 0, half + 0.025, 0)
  put(new THREE.BoxGeometry(RACK_W - 0.12, 0.04, 0.07), steel, 0, half - 0.01, RACK_D / 2 - 0.06)

  /* ── 19" 前后导轨（含每 U 安装孔） ── */
  const railGeo = new THREE.BoxGeometry(0.028, rackH - 0.12, 0.05)
  const railZ = [RACK_D / 2 - 0.13, -RACK_D / 2 + 0.13]
  for (const z of railZ) {
    for (const sx of [-1, 1]) put(railGeo, railMat, (sx * RAIL_SPAN) / 2, 0, z)
  }
  // 安装孔：每 U × 前后 × 左右，用 InstancedMesh 一次画完
  const holeH = Math.min(us * 0.4, 0.024)
  const holeGeo = new THREE.BoxGeometry(0.02, holeH, 0.006)
  const holes = new THREE.InstancedMesh(holeGeo, holeMat, capU * 4)
  const dummy = new THREE.Object3D()
  let hi = 0
  for (const z of railZ) {
    for (const sx of [-1, 1]) {
      for (let u = 1; u <= capU; u++) {
        dummy.position.set((sx * RAIL_SPAN) / 2, -half + 0.06 + (u - 0.5) * us, z + 0.028)
        dummy.updateMatrix()
        holes.setMatrixAt(hi++, dummy.matrix)
      }
    }
  }
  holes.count = hi
  holes.castShadow = false
  holes.receiveShadow = false
  group.add(holes)

  /* ── 设备：主体 + 前面板 + 提手 + 状态指示灯 ── */
  const innerW = RAIL_SPAN - 0.03
  const innerD = RACK_D - 0.3
  for (const b of blocks) {
    if (b.status === '空闲') continue
    const h = b.height * us - 0.012
    const base = STATUS_COLORS[b.status] ?? 0x64748b
    const body = new THREE.MeshStandardMaterial({ color: base, metalness: 0.35, roughness: 0.5 })
    const geo = new THREE.BoxGeometry(innerW, h, innerD)
    const mesh = new THREE.Mesh(geo, body)
    const bottom = -half + 0.06
    const cy = bottom + ((b.fromU + b.toU) / 2) * us
    mesh.position.set(0, cy, 0)
    mesh.castShadow = true
    mesh.receiveShadow = true
    mesh.userData.block = b

    const edges = new THREE.LineSegments(
      new THREE.EdgesGeometry(geo),
      new THREE.LineBasicMaterial({ color: 0x0f172a, transparent: true, opacity: 0.45 }),
    )
    mesh.add(edges)

    // 前面板（略深色，突出 2cm）
    const bezel = new THREE.Mesh(
      new THREE.BoxGeometry(innerW, h, 0.02),
      new THREE.MeshStandardMaterial({
        color: new THREE.Color(base).multiplyScalar(0.72),
        metalness: 0.4,
        roughness: 0.45,
      }),
    )
    bezel.position.set(0, 0, innerD / 2 + 0.01)
    bezel.castShadow = true
    mesh.add(bezel)

    // 两侧提手
    const handleGeo = new THREE.BoxGeometry(0.014, Math.max(h * 0.55, 0.02), 0.035)
    const handleMat = new THREE.MeshStandardMaterial({ color: 0x374151, metalness: 0.6, roughness: 0.4 })
    for (const sx of [-1, 1]) {
      const handle = new THREE.Mesh(handleGeo, handleMat)
      handle.position.set((sx * (innerW - 0.05)) / 2, 0, innerD / 2 + 0.035)
      handle.castShadow = true
      mesh.add(handle)
    }

    // 状态指示灯（emissive）
    const led = new THREE.Mesh(
      new THREE.BoxGeometry(0.022, 0.014, 0.008),
      new THREE.MeshStandardMaterial({
        color: LED_COLORS[b.status] ?? 0x64748b,
        emissive: LED_COLORS[b.status] ?? 0x64748b,
        emissiveIntensity: 1.2,
        roughness: 0.4,
      }),
    )
    led.position.set(innerW / 2 - 0.05, h / 2 - 0.02, innerD / 2 + 0.022)
    mesh.add(led)

    group.add(mesh)
    c.meshes.push(mesh)
  }

  /* ── 可选前门：门框 + 半透明网孔 + 把手 ── */
  if (showDoor.value) {
    const doorZ = RACK_D / 2 + 0.02
    const frameMat = steel
    const vBar = new THREE.BoxGeometry(0.045, rackH, 0.03)
    for (const sx of [-1, 1]) put(vBar, frameMat, (sx * (RACK_W - 0.05)) / 2, 0, doorZ)
    const hBar = new THREE.BoxGeometry(RACK_W - 0.1, 0.05, 0.03)
    put(hBar, frameMat, 0, half - 0.03, doorZ)
    put(hBar, frameMat, 0, -half + 0.03, doorZ)

    // 网孔玻璃门（半透明 + 网格线示意通风孔）
    const glass = new THREE.Mesh(
      new THREE.BoxGeometry(RACK_W - 0.1, rackH - 0.1, 0.006),
      new THREE.MeshStandardMaterial({
        color: 0xb8c6d6,
        metalness: 0.2,
        roughness: 0.35,
        transparent: true,
        opacity: 0.22,
        side: THREE.DoubleSide,
      }),
    )
    glass.position.set(0, 0, doorZ)
    group.add(glass)

    const pts: import('three').Vector3[] = []
    const w = (RACK_W - 0.1) / 2
    const hh = (rackH - 0.1) / 2
    for (let x = -w + 0.03; x < w; x += 0.03) {
      pts.push(new THREE.Vector3(x, -hh, doorZ + 0.005), new THREE.Vector3(x, hh, doorZ + 0.005))
    }
    for (let y = -hh + 0.03; y < hh; y += 0.03) {
      pts.push(new THREE.Vector3(-w, y, doorZ + 0.005), new THREE.Vector3(w, y, doorZ + 0.005))
    }
    const lg = new THREE.BufferGeometry().setFromPoints(pts)
    group.add(
      new THREE.LineSegments(
        lg,
        new THREE.LineBasicMaterial({ color: 0x64748b, transparent: true, opacity: 0.4 }),
      ),
    )

    // 门把手
    put(
      new THREE.BoxGeometry(0.03, 0.24, 0.05),
      steelDark,
      RACK_W / 2 - 0.09,
      0,
      doorZ + 0.035,
    )
  }

  applyGlow()
  fitCamera()
}

function fitCamera() {
  const c = ctx
  if (!c) return
  c.controls.target.set(0, 0, 0)
  c.camera.position.set(1.75, 0.5, 3.05)
  c.camera.lookAt(0, 0, 0)
}

async function initScene() {
  const host = hostRef.value
  if (!host) return
  loadError.value = ''
  try {
    const THREE = await import('three')
    const { OrbitControls } = await import('three/examples/jsm/controls/OrbitControls.js')

    const renderer = new THREE.WebGLRenderer({ antialias: true })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.setSize(host.clientWidth || 400, host.clientHeight || 500)
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.toneMapping = THREE.NeutralToneMapping
    renderer.toneMappingExposure = 1
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = THREE.PCFSoftShadowMap
    host.appendChild(renderer.domElement)

    const scene = new THREE.Scene()
    scene.background = new THREE.Color(BG)
    scene.fog = new THREE.Fog(BG, 9, 30)

    // 环境贴图：钢/导轨等金属面靠反射成像，否则 metalness 高的面会发黑
    const { RoomEnvironment } = await import('three/examples/jsm/environments/RoomEnvironment.js')
    const pmrem = new THREE.PMREMGenerator(renderer)
    const envTex = pmrem.fromScene(new RoomEnvironment(), 0.04).texture
    scene.environment = envTex
    pmrem.dispose()

    const camera = new THREE.PerspectiveCamera(
      42,
      (host.clientWidth || 400) / (host.clientHeight || 500),
      0.05,
      100,
    )
    camera.position.set(1.75, 0.5, 3.05)

    // 灯光：环境贴图（反射）+ 半球光 + 主光（投影）+ 侧补光
    scene.add(new THREE.AmbientLight(0xffffff, 0.32))
    scene.add(new THREE.HemisphereLight(0xffffff, 0xc9d2dc, 0.4))
    const key = new THREE.DirectionalLight(0xffffff, 1.7)
    key.position.set(3.2, 5.4, 3.6)
    key.castShadow = true
    key.shadow.mapSize.set(2048, 2048)
    key.shadow.camera.near = 0.5
    key.shadow.camera.far = 20
    key.shadow.camera.left = -3
    key.shadow.camera.right = 3
    key.shadow.camera.top = 4
    key.shadow.camera.bottom = -2
    key.shadow.bias = -0.0006
    key.shadow.normalBias = 0.02
    key.shadow.camera.updateProjectionMatrix()
    scene.add(key)
    const fill = new THREE.DirectionalLight(0xffffff, 0.7)
    fill.position.set(-4, 2.4, -2.5)
    scene.add(fill)

    // 地面（接影）+ 浅色网格
    const floor = new THREE.Mesh(
      new THREE.PlaneGeometry(30, 30),
      new THREE.MeshStandardMaterial({ color: 0xdfe4ea, metalness: 0, roughness: 0.95 }),
    )
    floor.rotation.x = -Math.PI / 2
    floor.position.y = floorY
    floor.receiveShadow = true
    scene.add(floor)
    const grid = new THREE.GridHelper(30, 30, 0xc3cad3, 0xd4dae1)
    const gm = grid.material as import('three').Material | import('three').Material[]
    const setGrid = (m: import('three').Material) => {
      m.transparent = true
      m.opacity = 0.55
      m.depthWrite = false
    }
    if (Array.isArray(gm)) gm.forEach(setGrid)
    else setGrid(gm)
    grid.position.y = floorY + 0.002
    scene.add(grid)

    const group = new THREE.Group()
    scene.add(group)

    const controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.dampingFactor = 0.08
    controls.minDistance = 1.4
    controls.maxDistance = 9
    controls.maxPolarAngle = Math.PI / 2.03

    ctx = {
      THREE,
      renderer,
      scene,
      camera,
      controls,
      raycaster: new THREE.Raycaster(),
      pointer: new THREE.Vector2(),
      group,
      floor,
      envTex,
      meshes: [],
      raf: 0,
      disposed: false,
    }

    rebuild()

    const tick = () => {
      if (!ctx || ctx.disposed) return
      ctx.controls.update()
      ctx.renderer.render(ctx.scene, ctx.camera)
      ctx.raf = requestAnimationFrame(tick)
    }
    tick()

    const ro = new ResizeObserver(() => {
      if (!ctx || !hostRef.value) return
      const w = hostRef.value.clientWidth
      const h = hostRef.value.clientHeight
      ctx.renderer.setSize(w, h)
      ctx.camera.aspect = w / (h || 1)
      ctx.camera.updateProjectionMatrix()
    })
    ro.observe(host)
    ctx.ro = ro

    renderer.domElement.addEventListener('pointermove', onPointerMove)
    renderer.domElement.addEventListener('pointerleave', onPointerLeave)
    renderer.domElement.addEventListener('click', onClick)
  } catch (e) {
    loadError.value = '3D 组件加载失败，请刷新重试'
    console.error(e)
  }
}

function pickBlock(e: MouseEvent): { block: UnitBlock; mesh: import('three').Mesh; x: number; y: number } | null {
  const c = ctx
  if (!c) return null
  const rect = c.renderer.domElement.getBoundingClientRect()
  c.pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
  c.pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
  c.raycaster.setFromCamera(c.pointer, c.camera)
  const hits = c.raycaster.intersectObjects(c.meshes, false)
  const hit = hits.find((h) => h.object.userData.block)
  if (!hit) return null
  return {
    block: hit.object.userData.block as UnitBlock,
    mesh: hit.object as import('three').Mesh,
    x: e.clientX - rect.left,
    y: e.clientY - rect.top,
  }
}

function onPointerMove(e: PointerEvent) {
  const pick = pickBlock(e)
  if (!pick || !ctx) {
    tooltip.value.show = false
    if (hoverMesh) {
      hoverMesh = null
      applyGlow()
    }
    if (ctx) ctx.renderer.domElement.style.cursor = 'grab'
    return
  }
  ctx.renderer.domElement.style.cursor = 'pointer'
  if (hoverMesh !== pick.mesh) {
    hoverMesh = pick.mesh
    applyGlow()
  }
  const b = pick.block
  tooltip.value = {
    show: true,
    x: pick.x + 14,
    y: pick.y + 10,
    text:
      b.status === '占用'
        ? `U${b.fromU}-U${b.toU} ${b.assetName || b.occupiedBy || ''}（${b.height}U）`
        : `U${b.fromU}-U${b.toU} ${b.status}`,
  }
}

function onPointerLeave() {
  tooltip.value.show = false
  if (hoverMesh) {
    hoverMesh = null
    applyGlow()
  }
}

function onClick(e: MouseEvent) {
  const pick = pickBlock(e)
  if (!pick) return
  selectedKey = blockKey(pick.block)
  applyGlow()
  emit('select', pick.block)
}

function toggleDoor() {
  showDoor.value = !showDoor.value
  rebuild()
}

watch(
  // 按引用比对即可：units 由父级每次加载后整体换新数组（units.value = res.units ?? []），
  // uCapacity 是原始值。刻意不要 deep —— deep 会每次触发都遍历全部 U 位块去挂嵌套依赖。
  [() => props.units, () => props.uCapacity],
  () => rebuild(),
)

onMounted(() => {
  void initScene()
})

onBeforeUnmount(() => {
  const c = ctx
  if (!c) return
  c.disposed = true
  cancelAnimationFrame(c.raf)
  c.ro?.disconnect()
  const el = c.renderer.domElement
  el.removeEventListener('pointermove', onPointerMove)
  el.removeEventListener('pointerleave', onPointerLeave)
  el.removeEventListener('click', onClick)
  clearGroup(c)
  c.controls.dispose()
  c.scene.traverse((obj) => {
    const mesh = obj as import('three').Mesh
    if (mesh.geometry) mesh.geometry.dispose()
    const m = mesh.material as import('three').Material | import('three').Material[] | undefined
    if (Array.isArray(m)) m.forEach((x) => x.dispose())
    else if (m) m.dispose()
  })
  c.renderer.dispose()
  c.envTex?.dispose()
  el.remove()
  ctx = null
})
</script>

<template>
  <div class="elev3d">
    <div ref="hostRef" class="elev3d-host"></div>
    <div v-if="loadError" class="elev3d-err">{{ loadError }}</div>
    <div
      v-if="tooltip.show"
      class="elev3d-tip"
      :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }"
    >
      {{ tooltip.text }}
    </div>
    <div class="elev3d-legend">
      <span><i style="background: #3b82f6"></i>占用</span>
      <span><i style="background: #f59e0b"></i>预留</span>
      <span><i style="background: #ef4444"></i>故障</span>
      <span v-if="rackCode" class="code">{{ rackCode }}</span>
    </div>
    <button class="elev3d-door" type="button" @click="toggleDoor">
      {{ showDoor ? '隐藏前门' : '显示前门' }}
    </button>
    <div class="elev3d-hint">拖拽旋转 · 滚轮缩放 · 点击设备查看详情</div>
  </div>
</template>

<style scoped>
.elev3d {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 480px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: hidden;
  background: #eef2f6;
}

.elev3d-host {
  width: 100%;
  height: 100%;
}

.elev3d-host :deep(canvas) {
  display: block;
}

.elev3d-tip {
  position: absolute;
  z-index: 3;
  pointer-events: none;
  background: rgba(15, 23, 42, 0.92);
  color: #f1f5f9;
  font-size: 12px;
  padding: 5px 9px;
  border-radius: 4px;
  white-space: nowrap;
  border: 1px solid rgba(148, 163, 184, 0.35);
}

.elev3d-hint {
  position: absolute;
  left: 10px;
  bottom: 8px;
  font-size: 11px;
  color: #64748b;
  pointer-events: none;
}

.elev3d-legend {
  position: absolute;
  top: 8px;
  left: 10px;
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #475569;
  pointer-events: none;
}

.elev3d-legend i {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  margin-right: 4px;
  vertical-align: -1px;
}

.elev3d-legend .code {
  font-family: Consolas, Monaco, monospace;
  color: #64748b;
}

.elev3d-door {
  position: absolute;
  top: 8px;
  right: 10px;
  z-index: 2;
  padding: 4px 10px;
  font-size: 12px;
  color: #334155;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  cursor: pointer;
}

.elev3d-door:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.elev3d-err {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #dc2626;
  font-size: 14px;
  background: rgba(238, 242, 246, 0.85);
}
</style>
